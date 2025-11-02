package dev.yourapp.blem3

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var txtStatus: TextView
    private lateinit var chkAuto: CheckBox
    private val adapter = DevAdapter { addr, name -> onPick(addr, name) }

    private val btAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner get() = btAdapter.bluetoothLeScanner

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        // Sau khi xin quyền xong → không làm gì, user bấm Quét lại
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rv = findViewById(R.id.rvDevices)
        txtStatus = findViewById(R.id.txtStatus)
        chkAuto = findViewById(R.id.chkAutostart)
        val btnScan: Button = findViewById(R.id.btnScan)
        val btnMap: Button = findViewById(R.id.btnMap)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        chkAuto.isChecked = Prefs.autoStart(this)
        chkAuto.setOnCheckedChangeListener { _, b -> Prefs.setAutoStart(this, b) }

        val sa = Prefs.savedAddr(this)
        val sn = Prefs.savedName(this)
        txtStatus.text = if (sa != null) "Thiết bị đã lưu: $sn ($sa)" else "Chưa lưu thiết bị"

        btnScan.setOnClickListener { tryScan() }
        btnMap.setOnClickListener { showKeyMapDialog() }

        // Nếu đã lưu thiết bị → chạy service để autoconnect
        startService(Intent(this, BleM3Service::class.java).apply {
            action = BleM3Service.ACTION_CONNECT
        })
    }

    private fun tryScan() {
        if (!btAdapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        // xin quyền
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        val missing = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            reqPerms.launch(missing.toTypedArray())
            return
        }

        adapter.clear()
        val set = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, set, scanCb)
        txtStatus.text = "Đang quét…"
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            // Gộp trùng địa chỉ
            if (!adapter.contains(d.address)) {
                adapter.add(d.name ?: "(Không tên)", d.address)
            }
        }
    }

    private fun onPick(addr: String, name: String) {
        scanner.stopScan(scanCb)
        Prefs.saveDevice(this, addr, name)
        txtStatus.text = "Đã chọn $name ($addr) – đang kết nối…"
        startService(Intent(this, BleM3Service::class.java).apply {
            action = BleM3Service.ACTION_CONNECT
            putExtra(BleM3Service.EXTRA_ADDR, addr)
        })
    }

    private fun showKeyMapDialog() {
        AlertDialog.Builder(this)
            .setTitle("Map phím thủ công")
            .setMessage(
                "Tính năng sẽ cho phép bạn gán report-code HID → phím media.\n" +
                        "Phiên bản demo chưa đọc HID, nhưng UI đã sẵn sàng."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // --- Adapter nhỏ gọn cho danh sách thiết bị ---
    class DevAdapter(private val onPick: (String, String) -> Unit) :
        RecyclerView.Adapter<DevVH>() {
        private val data = mutableListOf<Pair<String, String>>() // name, addr
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device, parent, false)
            return DevVH(v, onPick)
        }
        override fun onBindViewHolder(holder: DevVH, position: Int) =
            holder.bind(data[position])
        override fun getItemCount(): Int = data.size
        fun add(name: String, addr: String) { data += name to addr; notifyItemInserted(data.lastIndex) }
        fun clear() { data.clear(); notifyDataSetChanged() }
        fun contains(addr: String) = data.any { it.second == addr }
    }

    class DevVH(v: View, private val onPick: (String, String) -> Unit) :
        RecyclerView.ViewHolder(v) {
        private val tName: TextView = v.findViewById(R.id.txtName)
        private val tAddr: TextView = v.findViewById(R.id.txtAddr)
        fun bind(p: Pair<String, String>) {
            tName.text = p.first
            tAddr.text = p.second
            itemView.setOnClickListener { onPick(p.second, p.first) }
        }
    }
}
