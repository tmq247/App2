package dev.yourapp.blem3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BleM3Service : Service() {

    companion object {
        const val ACTION_CONNECT = "dev.yourapp.blem3.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "dev.yourapp.blem3.ACTION_DISCONNECT"
        const val EXTRA_ADDR = "addr"
        private const val CH_ID = "blem3_fg"
        private const val TAG = "BleM3Service"
    }

    private val btAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val scanner: BluetoothLeScanner? get() = btAdapter.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotif("Đang chờ kết nối BLE-M3"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val addr = intent.getStringExtra(EXTRA_ADDR) ?: Prefs.savedAddr(this)
                if (addr != null) startScanOrConnect(addr)
                else startScanForName("BLE-M3")
            }
            ACTION_DISCONNECT -> {
                gatt?.close(); gatt = null
                stopSelf()
            }
            else -> {
                // service được khởi động trần → thử autoconnect
                Prefs.savedAddr(this)?.let { startScanOrConnect(it) } ?: startScanForName("BLE-M3")
            }
        }
        return START_STICKY
    }

    private fun buildNotif(text: String): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH_ID, "BLE-M3 Foreground", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BLE-M3 Interceptor")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun startScanOrConnect(addr: String) {
        val dev = try { btAdapter.getRemoteDevice(addr) } catch (_: Exception) { null }
        if (dev != null) {
            Log.d(TAG, "Trying direct connect to $addr")
            connect(dev)
        } else {
            startScanForAddress(addr)
        }
    }

    private fun startScanForAddress(addr: String) {
        val filters = listOf(ScanFilter.Builder().setDeviceAddress(addr).build())
        val set = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(filters, set, scanCb)
    }

    private fun startScanForName(name: String) {
        val filters = listOf(ScanFilter.Builder().setDeviceName(name).build())
        val set = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(filters, set, scanCb)
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            Log.d(TAG, "Found: ${d.address} ${d.name}")
            scanner?.stopScan(this)
            Prefs.saveDevice(this@BleM3Service, d.address, d.name)
            connect(d)
        }
    }

    private fun connect(device: BluetoothDevice) {
        gatt?.close()
        gatt = device.connectGatt(this, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(1, buildNotif("Đang kết nối ${device.address}"))
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected, discovering services…")
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(1, buildNotif("Đã kết nối ${gatt.device.address}"))
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected")
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(1, buildNotif("Mất kết nối – đang chờ…"))
                this@BleM3Service.gatt?.close()
                this@BleM3Service.gatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // TODO: Đăng ký notify vào HID Report để nhận phím
            Log.d(TAG, "Services discovered: ${gatt.services.size}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
