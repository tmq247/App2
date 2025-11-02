package dev.yourapp.blem3

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager

// ... import thêm:
import android.bluetooth.BluetoothDevice
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
  private val scanned = mutableListOf<BluetoothDevice>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    findViewById<Button>(R.id.startBtn).setOnClickListener { if (hasAllRequired()) startSvc() else requestAllPerms() }
    findViewById<Button>(R.id.stopBtn).setOnClickListener { stopService(Intent(this, BleM3Service::class.java)) }

    // nút chọn thiết bị
    findViewById<Button>(R.id.pickBtn).setOnClickListener {
      if (!hasAllRequired()) { requestAllPerms(); return@setOnClickListener }
      // mở dialog hiển thị danh sách thiết bị mới quét được – ta yêu cầu Service scan 5s rồi gửi kết quả về broadcast
      sendBroadcast(Intent("blem3.ACTION_SCAN_ONCE"))
    }

    // nhận danh sách thiết bị từ Service
    registerReceiver(object : android.content.BroadcastReceiver() {
      override fun onReceive(c: Context?, i: Intent?) {
        if (i?.action == "blem3.ACTION_SCAN_RESULT") {
          val names = i.getStringArrayListExtra("names") ?: arrayListOf()
          val addrs = i.getStringArrayListExtra("addrs") ?: arrayListOf()
          scanned.clear()
          for (k in names.indices) {
            val d = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(addrs[k])
            scanned += d
          }
          AlertDialog.Builder(this@MainActivity)
            .setTitle("Chọn thiết bị")
            .setItems(names.toTypedArray()) { _, which ->
              val dev = scanned[which]
              applicationContext.savedAddr = dev.address
              applicationContext.savedName = names[which]
              Toast.makeText(this@MainActivity, "Đã lưu: ${names[which]}", Toast.LENGTH_SHORT).show()
              startSvc()
            }.show()
        }
      }
    }, android.content.IntentFilter("blem3.ACTION_SCAN_RESULT"))
  }
}
