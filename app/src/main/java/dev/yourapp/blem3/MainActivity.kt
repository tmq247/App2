package dev.yourapp.blem3

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import dev.yourapp.blem3.Prefs.savedAddr
import dev.yourapp.blem3.Prefs.savedName

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMS = 1001
    }

    private lateinit var tvInfo: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnPick: Button
    private lateinit var btnMap: Button

    /** Receiver trả kết quả scan một lần (từ Service) */
    private val scanReceiver = object : BroadcastReceiver() {
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

        // ===== UI thuần code, kết thúc splash ngay lập tức =====
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
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
        setContentView(root)

        // ==== Sự kiện nút ====
        btnStart.setOnClickListener {
            if (hasAllRequired()) startSvc() else requestAllPerms()
        }
        btnStop.setOnClickListener {
            stopService(Intent(this, BleM3Service::class.java))
            toast("Đã dừng service")
        }
        btnPick.setOnClickListener {
            if (!hasAllPermsOrAsk()) return@setOnClickListener
            sendBroadcast(Intent("blem3.ACTION_SCAN_ONCE"))
            toast("Đang quét… bấm 1 nút trên remote")
        }
        btnMap.setOnClickListener {
            startActivity(Intent(this, KeyMapActivity::class.java))
        }

        // Receiver nhận kết quả scan
        registerReceiver(scanReceiver, IntentFilter("blem3.ACTION_SCAN_RESULT"))

        Handler(Looper.getMainLooper()).post { showSaved() }
    }

    override fun onDestroy() {
        unregisterReceiver(scanReceiver)
        super.onDestroy()
    }

    // ===== Helpers =====
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

    private fun permissionsNeeded(): Array<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    private fun hasAllRequired(): Boolean =
        permissionsNeeded().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun requestAllPerms() = requestPermissions(permissionsNeeded(), REQ_PERMS)

    private fun hasAllPermsOrAsk(): Boolean {
        if (hasAllRequired()) return true
        requestAllPerms()
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startSvc()
            } else {
                toast("Cần cấp đủ Bluetooth/Location/Notifications")
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
