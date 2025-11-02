package dev.yourapp.blem3

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.app.NotificationManager
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

  private val reqPerms = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { res ->
    // Khi user trả lời xin quyền, chỉ start service nếu BLE permissions đã ok
    if (hasAllRequired()) startSvc()
    else Toast.makeText(this, "Cần cấp đủ quyền Bluetooth/Location/Notifications", Toast.LENGTH_SHORT).show()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    findViewById<Button>(R.id.startBtn).setOnClickListener {
      if (hasAllRequired()) startSvc() else requestAllPerms()
    }
    findViewById<Button>(R.id.stopBtn).setOnClickListener {
      stopService(Intent(this, BleM3Service::class.java))
    }
  }

  private fun startSvc() {
    try {
      startForegroundService(Intent(this, BleM3Service::class.java))
      Toast.makeText(this, "Đang kết nối BLE-M3…", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      Toast.makeText(this, "Không thể khởi động dịch vụ: ${e.message}", Toast.LENGTH_LONG).show()
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
    // Android 14+: FGS connected device
    if (Build.VERSION.SDK_INT >= 34) {
      // quyền này là normal (không cần runtime), chỉ kiểm tra cho chắc
      // không bắt buộc check runtime
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
