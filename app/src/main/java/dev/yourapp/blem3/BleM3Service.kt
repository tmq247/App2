package dev.yourapp.blem3

import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import no.nordicsemi.android.ble.BleManager
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
    // Bắt đầu quét bằng BluetoothLeScanner chuẩn
    startScanForName("BLE-M3") { device ->
      mgr.connect(device).retry(3, 1000).useAutoConnect(false).enqueue()
      stopScan()
      notify("Đã kết nối BLE-M3")
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

  // ====== Quét BLE bằng API chuẩn
  private fun startScanForName(targetName: String, onFound: (BluetoothDevice) -> Unit) {
    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    val scanner = adapter.bluetoothLeScanner ?: return
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    val filters = listOf(ScanFilter.Builder().setDeviceName(targetName).build())

    val cb = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        val dev = result.device ?: return
        if (dev.name == targetName) onFound(dev)
      }
    }
    scanner.startScan(filters, settings, cb)

    // stop sau 20s nếu chưa thấy
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

  // ====== BleManager tối giản
  inner class M3Manager(ctx: Context) : BleManager(ctx) {
    private var reportChar: BluetoothGattCharacteristic? = null

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

    override fun onDeviceDisconnected() {
      notify("Mất kết nối • đang quét lại…")
      // rớt kết nối thì quét lại
      startScanForName("BLE-M3") { device ->
        connect(device).retry(3, 1000).useAutoConnect(false).enqueue()
        stopScan()
        notify("Đã kết nối BLE-M3")
      }
    }

    override fun onServicesInvalidated() { reportChar = null }
  }

  // ====== Map report thô -> hành động media/Zello
  private fun handleReport(bytes: ByteArray?) {
    if (bytes == null || bytes.isEmpty()) return

    // dò media usage trước nếu có
    for (b in bytes) {
      when (b.toInt() and 0xFF) {
        0x80 -> { media.volUp(); return }     // Volume Up
        0x81 -> { media.volDown(); return }   // Volume Down
        0xB5 -> { media.next(); return }      // Next Track
        0xB6 -> { media.prev(); return }      // Prev Track
        0xCD -> { media.playPause(); return } // Play/Pause
      }
    }

    // fallback kiểu chuột: btn bit0 = left click -> Play/Pause
    val btn = bytes[0].toInt() and 0xFF
    if ((btn and 0x01) != 0) { media.playPause(); return }

    // dx heuristic: >5 next, <-5 prev
    val dx = bytes.getOrNull(1)?.toInt() ?: 0
    if (dx > 5) media.next() else if (dx < -5) media.prev()
  }
}
