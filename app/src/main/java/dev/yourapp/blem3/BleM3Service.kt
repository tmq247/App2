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
        startForeground(1, notif("Đang khởi động dịch vụ BLE-M3"))
        media = MediaOut(this)
        mgr = M3Manager(this)

        // Nhận yêu cầu scan 1 lần từ Activity -> gửi danh sách về
        registerReceiver(object: android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == "blem3.ACTION_SCAN_ONCE") scanOnceAndReply()
            }
        }, android.content.IntentFilter("blem3.ACTION_SCAN_ONCE"))

        mgr.setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnected(device: BluetoothDevice) {
                notify("Đã kết nối: ${device.name ?: device.address}")
            }
            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                notify("Mất kết nối • đang quét lại…")
                autoConnectOrScan()
            }
            override fun onDeviceConnecting(device: BluetoothDevice) {}
            override fun onDeviceDisconnecting(device: BluetoothDevice) {}
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                notify("Kết nối thất bại ($reason) • đang quét lại…")
                autoConnectOrScan()
            }
            override fun onDeviceReady(device: BluetoothDevice) {}
        })

        // Tự kết nối MAC đã lưu nếu có
        autoConnectOrScan()
    }

    private fun autoConnectOrScan() {
        val saved = applicationContext.savedAddr
        if (saved != null) {
            val dev = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                .adapter.getRemoteDevice(saved)
            notify("Đang kết nối $saved…")
            mgr.connect(dev).retry(3,1000).useAutoConnect(true).enqueue()
        } else {
            startScanForHid { device ->
                applicationContext.savedAddr = device.address
                applicationContext.savedName = device.name
                mgr.connect(device).retry(3,1000).useAutoConnect(false).enqueue()
                stopScan()
            }
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

    // ====== Scan theo HID UUID + fallback tên ======
    private fun startScanForHid(onFound: (BluetoothDevice) -> Unit) {
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btMgr.adapter ?: return
        val scanner: BluetoothLeScanner = adapter.bluetoothLeScanner ?: return

        val HID_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"))

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

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

    // Scan 1 lần để trả danh sách cho Activity chọn
    private fun scanOnceAndReply() {
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

    // ====== BleManager ======
    inner class M3Manager(ctx: Context) : BleManager(ctx) {
        private var reportChar: BluetoothGattCharacteristic? = null

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val hid = gatt.getService(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")) ?: return false
            reportChar = hid.getCharacteristic(UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb"))
            return reportChar != null
        }

        override fun initialize() {
            setNotificationCallback(reportChar).with { _, data -> handleReport(data.value) }
            enableNotifications(reportChar).enqueue()
            notify("Đã kết nối • đang nghe phím…")
        }

        override fun onServicesInvalidated() { reportChar = null }
    }

    // Gửi raw usage cho màn key-map
    private fun sendRawUsage(u: Int) {
        val i = Intent("blem3.ACTION_KEY_RAW").putExtra("usage", u)
        sendBroadcast(i)
    }

    // ====== Giải mã + Map phím ======
    private fun handleReport(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        val userMap = Prefs.loadMap(this)

        // Ưu tiên: kiểm tra từng byte như usage code
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            sendRawUsage(u) // để KeyMapActivity bắt mã

            // Map theo người dùng
            when (Action.fromLabel(userMap[u] ?: "")) {
                Action.PLAY_PAUSE -> { media.playPause(); return }
                Action.NEXT       -> { media.next(); return }
                Action.PREV       -> { media.prev(); return }
                Action.VOL_UP     -> { media.volUp(); return }
                Action.VOL_DOWN   -> { media.volDown(); return }
                else -> { /* không map -> thử mặc định */ }
            }

            // Mặc định theo chuẩn Consumer Control
            when (u) {
                0x80 -> { media.volUp(); return }     // Volume Up
                0x81 -> { media.volDown(); return }   // Volume Down
                0xB5 -> { media.next(); return }      // Next Track
                0xB6 -> { media.prev(); return }      // Previous Track
                0xCD -> { media.playPause(); return } // Play/Pause
            }
        }

        // Fallback kiểu chuột: btn bit0 = left click -> Play/Pause
        val btn = bytes[0].toInt() and 0xFF
        if ((btn and 0x01) != 0) { media.playPause(); return }

        // Dịch chuyển ngang → next/prev
        val dx = bytes.getOrNull(1)?.toInt() ?: 0
        if (dx > 5) media.next() else if (dx < -5) media.prev()
    }
}
