package dev.yourapp.blem3

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import dev.yourapp.blem3.Prefs.savedAddr
import dev.yourapp.blem3.Prefs.savedName

class MainActivity : Activity() {

    private lateinit var tvInfo: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnPick: Button
    private lateinit var btnMap: Button

    /** Xin quyền → nếu đủ thì start service */
    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasAllRequired()) startSvc()
        else toast("Cần cấp đủ Bluetooth/Location/Notifications")
    }

    /** Nhận danh sách thiết bị sau khi scan một lần */
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

        // ---- UI thuần code để chắc chắn vẽ frame đầu tiên ngay lập tức ----
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)       // nền trắng -> không còn "đen"
            setPadding((20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt())
        }

        tvInfo = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            text = "BLE-M3 Interceptor\nNhấn START để quét/kết nối. Không pair remote trong Settings."
        }
        fun makeBtn(label: String) = Button(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
        }

        btnStart = makeBtn("START SERVICE")
        btnStop  = makeBtn("STOP")
        btnPick  = makeBtn("CHỌN THIẾT BỊ")
        btnMap   = makeBtn("GÁN PHÍM (Key Mapping)")

        fun add(v: View) {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dp).toInt() }
            root.addView(v, lp)
        }
        add(tvInfo); add(btnStart); add(btnStop); add(btnPick); add(btnMap)

        // Đặt content ngay (kết thúc splash ngay lập tức)
        setContentView(root)

        // Binding sự kiện (đặt sau setContentView cho chắc)
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

        // Đăng ký receiver
        registerReceiver(scanReceiver, IntentFilter("blem3.ACTION_SCAN_RESULT"))

        // Cập nhật thông tin MAC đã lưu (nếu có) một nhịp ngắn sau khi hiển thị
        Handler(Looper.getMainLooper()).post { showSaved() }
    }

    override fun onDestroy() {
        unregisterReceiver(scanReceiver)
        super.onDestroy()
    }

    // -------- Helpers ----------
    private fun showSaved() {
        val mac = applicationContext.savedAddr
        val name = applicationContext.savedName
        tvInfo.text = buildString {
            appendLine("BLE-M3 Interceptor")
            appendLine("Nhấn START để quét/kết nối. Không pair remote trong Settings.")
            if (!mac.isNullOrBlank()) appendLine("Đã lưu: ${name ?: "(no name)"} [$mac]")
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

    private fun startSvc() {
        try {
            startForegroundService(Intent(this, BleM3Service::class.java))
            toast("Đang kết nối…")
        } catch (e: Exception) {
            toast("Lỗi khởi động service: ${e.message}")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
