package dev.yourapp.blem3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.*

class BleM3Service : Service() {
    private lateinit var media: MediaOut
    private lateinit var mgr: M3Manager
    private var scanJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        media = MediaOut(this)
        mgr = M3Manager(this)

        startForeground(1, notif("Đang chờ kết nối BLE-M3"))

        // Theo dõi trạng thái kết nối BLE
        mgr.setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnected(device: BluetoothDevice) {
                notify("Đã kết nối BLE-M3")
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                notify("Mất kết nối • đang quét lại…")
                startScanForName("BLE-M3") { dev ->
                    mgr.connect(dev).retry(3, 1000).useAutoConnect(false).enqueue()
                    stopScan()
                }
            }

            override fun onDeviceConnecting(device: BluetoothDevice) {}
            override fun onDeviceDisconnecting(device: BluetoothDevice) {}
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {}
            override fun onDeviceReady(device: BluetoothDevice) {}
        })

        // Bắt đầu quét
        startScanForName("BLE-M3") { device ->
            mgr.connect(device).retry(3, 1000).useAutoConnect(false).enqueue()
            stopScan()
        }
    }

    private fun notify(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, notif(text))
    }

    private fun notif(text: String): Notification {
        val chId = "ble_m3"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(chId, "BLE-M3", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("BLE-M3 Interceptor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ====== Quét BLE ======
    private fun startScanForName(targetName: String, onFound: (BluetoothDevice) -> Unit) {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner: BluetoothLeScanner = adapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf(
            ScanFilter.Builder().setDeviceName(targetName).build()
        )

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val name = dev.name ?: return
                if (name == targetName) {
                    onFound(dev)
                }
            }
        }

        scanner.startScan(filters, settings, cb)

        // stop sau 20 giây nếu chưa thấy
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.Main).launch {
            delay(20_000)
            scanner.stopScan(cb)
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    // ====== BLE Manager ======
    inner class M3Manager(ctx: Context) : BleManager(ctx) {
        private var reportChar: android.bluetooth.BluetoothGattCharacteristic? = null

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val hid = gatt.getService(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")) ?: return false
            reportChar = hid.getCharacteristic(UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb"))
            return reportChar != null
        }

        override fun initialize() {
            setNotificationCallback(reportChar).with { _, data ->
                handleReport(data.value)
            }
            enableNotifications(reportChar).enqueue()
        }

        override fun onServicesInvalidated() {
            reportChar = null
        }
    }

    // ====== Giải mã dữ liệu HID ======
    private fun handleReport(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return

        // Ưu tiên mã usage của Media
        for (b in bytes) {
            when (b.toInt() and 0xFF) {
                0x80 -> { media.volUp(); return }     // Volume Up
                0x81 -> { media.volDown(); return }   // Volume Down
                0xB5 -> { media.next(); return }      // Next Track
                0xB6 -> { media.prev(); return }      // Previous Track
                0xCD -> { media.playPause(); return } // Play/Pause
            }
        }

        // Nếu là chuột: click trái → Play/Pause
        val btn = bytes[0].toInt() and 0xFF
        if ((btn and 0x01) != 0) {
            media.playPause()
            return
        }

        // Dịch chuyển ngang → next/prev
        val dx = bytes.getOrNull(1)?.toInt() ?: 0
        if (dx > 5) media.next() else if (dx < -5) media.prev()
    }
}
