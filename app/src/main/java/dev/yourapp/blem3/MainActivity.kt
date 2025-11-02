package dev.yourapp.blem3

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

  private val reqPerms = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { /* ignore */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val startBtn: Button = findViewById(R.id.startBtn)
    val stopBtn: Button  = findViewById(R.id.stopBtn)

    startBtn.setOnClickListener {
      startForegroundService(Intent(this, BleM3Service::class.java))
    }
    stopBtn.setOnClickListener {
      stopService(Intent(this, BleM3Service::class.java))
    }

    requestAllPerms()
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
