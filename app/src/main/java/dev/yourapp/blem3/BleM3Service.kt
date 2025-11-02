package dev.yourapp.blem3

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.stateAsFlow
import no.nordicsemi.android.ble.ktx.suspend
import java.util.*

class BleM3Service : Service() {
    private lateinit var media: MediaOut
    private lateinit var mgr: M3Manager

    override fun onCreate() {
        super.onCreate()
        media = MediaOut(this)
        mgr = M3Manager(this)

        startForeground(1, notif("Đang chờ kết nối BLE-M3"))
        // quét & kết nối lần đầu
        mgr.autoConnectByName("BLE-M3") { notif("Đã kết nối BLE-M3") }
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

    inner class M3Manager(ctx: Context) : BleManager(ctx) {
        private var reportChar = null as android.bluetooth.BluetoothGattCharacteristic?

        fun autoConnectByName(name: String, onConnected: () -> Unit) {
            scope.launchWhenCreated {
                scanner.observeDevices().collect { dev ->
                    if (dev.name == name) {
                        scanner.stop()
                        connect(dev.device).retry(3, 1000).useAutoConnect(true).enqueue()
                    }
                }
            }
            scanner.start()
            // subscribe state flow
            stateAsFlow().collectIn(scope) { state ->
                if (state.isConnected) onConnected()
            }
        }

        override fun isRequiredServiceSupported(gatt: android.bluetooth.BluetoothGatt): Boolean {
            val hid = gatt.getService(GattIds.SVC_HID) ?: return false
            reportChar = hid.getCharacteristic(GattIds.CHR_REPORT)
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

    // ==== Giải mã thô: BLE-M3 gửi 2 luồng: Keyboard & Mouse
    // Ta map nhanh:
    // - Nếu report có usage VolumeUp/Down -> vol
    // - Nếu là chuột Left/Right/Up/Down/Click -> Play/Next/Prev/… (tuỳ biến)
    private fun handleReport(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return

        // Một số BLE-M3 gửi loại "mouse": [buttons, dx, dy, wheel]
        // buttons bit0=Left, bit1=Right; dx/dy có thể 0 khi chỉ click.
        // Một số gửi keyboard report với mảng usage codes (0x80.. cho media).
        // Ở đây map thực dụng: ưu tiên volume nếu phát hiện code 0x81/0x80, còn lại xem "left click" là Play/Pause.

        // dò volume trong mảng
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0x80) { media.volUp();   return }    // VolumeUp
            if (v == 0x81) { media.volDown(); return }    // VolumeDown
            if (v == 0xB5) { media.next();    return }    // Scan Next Track (nếu có)
            if (v == 0xB6) { media.prev();    return }    // Scan Prev Track
            if (v == 0xCD) { media.playPause(); return }  // Play/Pause
        }

        // fallback kiểu chuột: nếu byte0 (buttons) có bit0 -> click trái -> Play/Pause
        val btn = bytes[0].toInt() and 0xFF
        if ((btn and 0x01) != 0) {
            media.playPause()
            return
        }

        // nếu dx > 0 coi như Next, dx < 0 coi như Prev
        val dx = bytes.getOrNull(1)?.toInt() ?: 0
        if (dx > 5) media.next() else if (dx < -5) media.prev()
    }
}
