package dev.yourapp.blem3

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dev.yourapp.blem3.databinding.ActivityMainBinding
import android.content.Intent

class MainActivity : AppCompatActivity() {
  private lateinit var vb: ActivityMainBinding

  private val reqPerms = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { /* ignore */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    vb = ActivityMainBinding.inflate(layoutInflater)
    setContentView(vb.root)

    vb.startBtn.setOnClickListener {
      startForegroundService(Intent(this, BleM3Service::class.java))
    }
    vb.stopBtn.setOnClickListener {
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
