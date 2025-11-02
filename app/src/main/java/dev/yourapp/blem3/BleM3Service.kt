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
import dev.yourapp.blem3.Prefs.savedAddr
import dev.yourapp.blem3.Prefs.savedName
import dev.yourapp.blem3.Prefs.loadMap

class BleM3Service : Service() {
    private lateinit var media: MediaOut
    private var scanJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notif("Đang khởi động dịch vụ BLE-M3"))
        media = MediaOut(this)

        if (!hasBtPerms()) {
            notify("Thiếu quyền Bluetooth/Location — mở ứng dụng để cấp quyền")
            return
        }

        // Lắng nghe scan 1 lần để trả danh sách cho MainActivity
        registerReceiver(object: android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == "blem3.ACTION_SCAN_ONCE") scanOnceAndReply()
            }
        }, android.content.IntentFilter("blem3.ACTION_SCAN_ONCE"))

        autoConnectOrScan()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasBtPerms(): Boolean {
        fun ok(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        return ok(Manifest.permission.BLUETOOTH_CONNECT)
                && ok(Manifest.permission.BLUETOOTH_SCAN)
                && ok(Manifest.permission.ACCESS_FINE_LOCATION)
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
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)  // <= sửa ở đây
            .build()
    }
    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, notif(text))
    }

    private fun autoConnectOrScan() {
        if (!hasBtPerms()) { notify("Thiếu quyền — mở ứng dụng để cấp quyền"); return }
        val saved = applicationContext.savedAddr()
        if (saved != null) {
            val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val dev = btMgr.adapter.getRemoteDevice(saved)
            notify("Đang kết nối $saved…")
            dev.connectGatt(this, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        } else {
            startScanForHid { device ->
                Prefs.saveDevice(this, device.address, device.name)
                device.connectGatt(this, false, gattCb, BluetoothDevice.TRANSPORT_LE)
                stopScan()
            }
        }
    }

    // ====== GATT callback (tối giản) ======
    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                notify("Đã kết nối: ${gatt.device.name ?: gatt.device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notify("Mất kết nối • đang quét lại…")
                autoConnectOrScan()
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // TODO: enable notify HID report tại đây khi bạn sẵn sàng
        }
    }

    // ===== Scan theo HID UUID + fallback tên =====
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

    // Scan 1 lần để trả danh sách cho Activity
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

    // === bắn mã phím thô (dùng cho KeyMapActivity) – sẽ gọi ở handleReport khi bạn bật notify HID ===
    private fun sendRawUsage(u: Int) {
        val i = Intent("blem3.ACTION_KEY_RAW").putExtra("usage", u)
        sendBroadcast(i)
    }
}
