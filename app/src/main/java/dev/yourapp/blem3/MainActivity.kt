package dev.yourapp.blem3

import android.Manifest
import android.content.*
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import dev.yourapp.blem3.Prefs.savedAddr
import dev.yourapp.blem3.Prefs.savedName

class MainActivity : AppCompatActivity() {

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasAllRequired()) startSvc() else
            Toast.makeText(this, "Cần cấp đủ quyền Bluetooth/Location/Notifications", Toast.LENGTH_SHORT).show()
    }

    private val scanReceiver = object: BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action != "blem3.ACTION_SCAN_RESULT") return
            val names = i.getStringArrayListExtra("names") ?: arrayListOf()
            val addrs = i.getStringArrayListExtra("addrs") ?: arrayListOf()
            if (names.isEmpty()) {
                Toast.makeText(this@MainActivity, "Không tìm thấy thiết bị. Bấm 1 nút trên remote rồi thử lại.", Toast.LENGTH_LONG).show()
                return
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Chọn thiết bị")
                .setItems(names.toTypedArray()) { _, which ->
                    val addr = addrs[which]
                    val dev: BluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(addr)
                    applicationContext.savedAddr = addr
                    applicationContext.savedName = names[which]
                    Toast.makeText(this@MainActivity, "Đã lưu: ${names[which]}", Toast.LENGTH_SHORT).show()
                    startSvc()
                }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReceiver(scanReceiver, IntentFilter("blem3.ACTION_SCAN_RESULT"))

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            if (hasAllRequired()) startSvc() else requestAllPerms()
        }
        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, BleM3Service::class.java))
        }
        findViewById<Button>(R.id.pickBtn).setOnClickListener {
            if (!hasAllRequired()) { requestAllPerms(); return@setOnClickListener }
            sendBroadcast(Intent("blem3.ACTION_SCAN_ONCE"))
        }
        findViewById<Button>(R.id.mapBtn).setOnClickListener {
            startActivity(Intent(this, KeyMapActivity::class.java))
        }
    }

    override fun onDestroy() {
        unregisterReceiver(scanReceiver)
        super.onDestroy()
    }

    private fun startSvc() {
        try {
            startForegroundService(Intent(this, BleM3Service::class.java))
            Toast.makeText(this, "Đang kết nối…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khởi động service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasAllRequired(): Boolean {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 31) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestAllPerms() {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        reqPerms.launch(list.toTypedArray())
    }
}
