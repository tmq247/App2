package dev.yourapp.blem3

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import dev.yourapp.blem3.Prefs.savedAddr
import dev.yourapp.blem3.Prefs.savedName

class MainActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnPick: Button
    private lateinit var btnMap: Button

    // xin quyền -> nếu đủ thì start service
    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasAllRequired()) startSvc()
        else toast("Cần cấp đủ Bluetooth/Location/Notifications")
    }

    // nhận danh sách thiết bị sau khi scan một lần
    private val scanReceiver = object: BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action != "blem3.ACTION_SCAN_RESULT") return
            val names = i.getStringArrayListExtra("names") ?: arrayListOf()
            val addrs = i.getStringArrayListExtra("addrs") ?: arrayListOf()
            if (names.isEmpty()) {
                toast("Không thấy thiết bị. Hãy bấm 1 nút trên remote rồi thử lại.")
                return
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Chọn thiết bị")
                .setItems(names.toTypedArray()) { _, which ->
                    applicationContext.savedAddr = addrs[which]
                    applicationContext.savedName = names[which]
                    toast("Đã lưu: ${names[which]}")
                    startSvc()
                    showSaved()
                }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ===== UI thuần code (không dùng XML) =====
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)           // nền trắng -> không thể “đen”
            setPadding((24 * resources.displayMetrics.density).toInt())
        }

        tvInfo = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            text = "BLE-M3 Interceptor\nNhấn START để quét/kết nối. Không pair remote trong Settings."
        }
        btnStart = Button(this).apply { text = "START SERVICE" }
        btnStop  = Button(this).apply { text = "STOP" }
        btnPick  = Button(this).apply { text = "CHỌN THIẾT BỊ" }
        btnMap   = Button(this).apply { text = "GÁN PHÍM (Key Mapping)" }

        fun addGap(v: View) {
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
            root.addView(v, p)
        }

        addGap(tvInfo)
        addGap(btnStart); addGap(btnStop); addGap(btnPick); addGap(btnMap)
        setContentView(root)

        // ===== Hành vi nút =====
        btnStart.setOnClickListener {
            if (hasAllRequired()) startSvc() else requestAllPerms()
        }
        btnStop.setOnClickListener {
            stopService(Intent(this, BleM3Service::class.java))
            toast("Đã dừng service")
        }
        btnPick.setOnClickListener {
            if (!hasAllRequired()) { requestAllPerms(); return@setOnClickListener }
            sendBroadcast(Intent("blem3.ACTION_SCAN_ONCE"))
            toast("Đang quét… bấm 1 nút trên remote")
        }
        btnMap.setOnClickListener {
            startActivity(Intent(this, KeyMapActivity::class.java))
        }

        // receiver trả kết quả scan
        registerReceiver(scanReceiver, IntentFilter("blem3.ACTION_SCAN_RESULT"))

        // hiển thị MAC đã lưu (nếu có)
        showSaved()
    }

    override fun onDestroy() {
        unregisterReceiver(scanReceiver)
        super.onDestroy()
    }

    // ===== helpers =====
    private fun showSaved() {
        val mac = applicationContext.savedAddr
        val name = applicationContext.savedName
        tvInfo.text = buildString {
            appendLine("BLE-M3 Interceptor")
            appendLine("Nhấn START để quét/kết nối. Không pair remote trong Settings.")
            if (!mac.isNullOrBlank()) appendLine("Đã lưu: ${name ?: "(no name)"} [$mac]")
        }
    }

    private fun startSvc() {
        try {
            startForegroundService(Intent(this, BleM3Service::class.java))
            toast("Đang kết nối…")
        } catch (e: Exception) {
            toast("Lỗi khởi động service: ${e.message}")
        }
    }

    private fun hasAllRequired(): Boolean {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            need += Manifest.permission.BLUETOOTH_SCAN
            need += Manifest.permission.BLUETOOTH_CONNECT
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        return need.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestAllPerms() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            need += Manifest.permission.BLUETOOTH_SCAN
            need += Manifest.permission.BLUETOOTH_CONNECT
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        reqPerms.launch(need.toTypedArray())
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
