package dev.yourapp.blem3

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.all { it }) startServiceOk()
        else Toast.makeText(this, "Cần cấp đủ quyền Bluetooth / Location / Notification", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startPermissionCheck()
    }

    private fun startPermissionCheck() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            startServiceOk()
        } else launcher.launch(perms.toTypedArray())
    }

    private fun startServiceOk() {
        try {
            val i = Intent(this, BleM3Service::class.java)
            startForegroundService(i)
            Toast.makeText(this, "Đang chạy dịch vụ BLE-M3", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khởi động service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
