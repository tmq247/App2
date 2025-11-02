package dev.yourapp.blem3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.*
import java.util.*

class BleM3Service : Service() {

    companion object {
        const val ACTION_CONNECT = "dev.yourapp.blem3.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "dev.yourapp.blem3.ACTION_DISCONNECT"
        const val EXTRA_ADDR = "addr"
    }

    private var scanJob: Job? = null
    private var gatt: BluetoothGatt? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notif("Đang khởi động dịch vụ BLE-M3"))

        if (!hasBtPerms()) {
            notify("Thiếu quyền Bluetooth/Location — mở ứng dụng để cấp quyền")
            return
        }

        // Lắng nghe yêu cầu scan 1 lần từ MainActivity
        registerReceiver(object: android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == "blem3.ACTION_SCAN_ONCE") scanOnceAndReply()
            }
        }, android.content.IntentFilter("blem3.ACTION_SCAN_ONCE"))

        autoConnectOrScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val addr = intent.getStringExtra(EXTRA_ADDR) ?: Prefs.savedAddr(this)
                if (addr != null) startScanOrConnect(addr) else autoConnectOrScan()
            }
            ACTION_DISCONNECT -> {
                gatt?.close(); gatt = null
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** ---- Quyền ---- */
    private fun hasBtPerms(): Boolean {
        fun ok(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        return ok(Manifest.permission.BLUETOOTH_CONNECT)
                && ok(Manifest.permission.BLUETOOTH_SCAN)
                && ok(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** ---- Notification helpers ---- */
    private fun notif(text: String): Notification {
        val chId = "ble_m3"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(chId, "BLE-M3", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("BLE-M3 Interceptor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // dùng icon hệ thống
            .setOngoing(true)
            .build()
    }
    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, notif(text))
    }

    /** ---- Kết nối theo MAC đã lưu; nếu chưa có thì scan HID ---- */
    private fun autoConnectOrScan() {
        if (!hasBtPerms()) { notify("Thiếu quyền — mở ứng dụng để cấp quyền"); return }
        val saved = Prefs.savedAddr(this)
        if (saved != null) {
            startScanOrConnect(saved)
        } else {
            startScanForHid { device ->
                Prefs.saveDevice(this, device.address, device.name)
                connect(device)
                stopScan()
            }
        }
    }

    private fun startScanOrConnect(addr: String) {
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val dev = try { btMgr.adapter.getRemoteDevice(addr) } catch (_: Exception) { null }
        if (dev != null) {
            notify("Đang kết nối $addr…")
            connect(dev)
        } else {
            // Không resolve được device → thử scan theo address
            val scanner = btMgr.adapter.bluetoothLeScanner ?: return
            val filters = listOf(ScanFilter.Builder().setDeviceAddress(addr).build())
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            notify("Đang quét $addr …")
            scanner.startScan(filters, settings, object: ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    Prefs.saveDevice(this@BleM3Service, result.device.address, result.device.name)
                    connect(result.device)
                }
            })
        }
    }

    private fun connect(device: BluetoothDevice) {
        gatt?.close()
        gatt = device.connectGatt(this, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                notify("Đã kết nối: ${gatt.device.name ?: gatt.device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notify("Mất kết nối • đang quét lại…")
                this@BleM3Service.gatt?.close()
                this@BleM3Service.gatt = null
                autoConnectOrScan()
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // TODO: enableNotify vào HID Report tại đây khi bạn sẵn sàng
        }
    }

    /** ---- Scan HID + fallback theo tên ---- */
    private fun startScanForHid(onFound: (BluetoothDevice) -> Unit) {
        if (!hasBtPerms()) { notify("Thiếu quyền — mở ứng dụng để cấp quyền"); return }
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = btMgr.adapter.bluetoothLeScanner ?: return

        val HID_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"))
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(HID_UUID).build(),
            ScanFilter.Builder().setDeviceName("BLE-M3").build(),
            ScanFilter.Builder().setDeviceName("BLE M3").build()
        )

        notify("Đang quét thiết bị HID… (hãy bấm 1 nút trên remote)")

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val name = dev.name ?: ""
                val hasHid = result.scanRecord?.serviceUuids?.any { it.uuid == HID_UUID.uuid } == true
                if (hasHid || name.startsWith("BLE-M3", true) || name.startsWith("BLE M3", true)) {
                    notify("Tìm thấy ${name.ifBlank { dev.address }} • đang kết nối…")
                    onFound(dev)
                }
            }
        }

        scanner.startScan(filters, settings, cb)

        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.Main).launch {
            delay(60_000)
            notify("Không thấy remote • bấm 1 nút trên remote rồi nhấn START lại")
            scanner.stopScan(cb)
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    /** ---- Scan 1 lần để trả danh sách cho Activity ---- */
    private fun scanOnceAndReply() {
        if (!hasBtPerms()) { notify("Thiếu quyền — mở ứng dụng để cấp quyền"); return }
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = btMgr.adapter.bluetoothLeScanner ?: return
        val HID_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"))

        val names = arrayListOf<String>()
        val addrs = arrayListOf<String>()
        val seen = hashSetOf<String>()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val addr = dev.address ?: return
                if (!seen.add(addr)) return
                val hasHid = result.scanRecord?.serviceUuids?.any { it.uuid == HID_UUID.uuid } == true
                val name = dev.name?.takeIf { it.isNotBlank() } ?: if (hasHid) "HID $addr" else addr
                names += name; addrs += addr
            }
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(HID_UUID).build(),
            ScanFilter.Builder().setDeviceName("BLE-M3").build(),
            ScanFilter.Builder().setDeviceName("BLE M3").build()
        )
        scanner.startScan(filters, settings, cb)

        CoroutineScope(Dispatchers.Main).launch {
            delay(5_000)
            scanner.stopScan(cb)
            val i = Intent("blem3.ACTION_SCAN_RESULT")
                .putStringArrayListExtra("names", names)
                .putStringArrayListExtra("addrs", addrs)
            sendBroadcast(i)
        }
    }

    /** Phát mã phím thô (gửi cho KeyMapActivity qua broadcast) – sẽ dùng khi bật notify HID */
    private fun sendRawUsage(u: Int) {
        val i = Intent("blem3.ACTION_KEY_RAW").putExtra("usage", u)
        sendBroadcast(i)
    }
}
