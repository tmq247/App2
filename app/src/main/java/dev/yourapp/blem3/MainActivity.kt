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

    private val btMgr by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val btAdapter: BluetoothAdapter? get() = btMgr.adapter
    private val scanner get() = btAdapter?.bluetoothLeScanner

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        // Sau khi xin quyền xong, người dùng bấm "Quét & chọn thiết bị" lại
        updateStatus()
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

        updateStatus()

        btnScan.setOnClickListener { tryScan() }
        btnMap.setOnClickListener { startActivity(Intent(this, KeyMapActivity::class.java)) }

        // KHÔNG auto start service ở đây nữa để tránh crash khi thiếu quyền/BT tắt.
        // Nếu muốn tự nối khi đã lưu thiết bị và đủ quyền, hãy bấm "Quét & chọn thiết bị" 1 lần để lưu.
    }

    private fun updateStatus() {
        val sa = Prefs.savedAddr(this)
        val sn = Prefs.savedName(this)
        txtStatus.text = if (sa != null) "Thiết bị đã lưu: $sn ($sa)" else "Chưa lưu thiết bị"
    }

    private fun tryScan() {
        // Kiểm tra adapter null/BT tắt
        val adapter = btAdapter
        if (adapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ Bluetooth", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            Toast.makeText(this, "Hãy bật Bluetooth rồi bấm Quét lại", Toast.LENGTH_LONG).show()
            return
        }

        // Quyền runtime
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        val missing = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            reqPerms.launch(missing.toTypedArray())
            return
        }

        // Bắt đầu quét
        adapter.startDiscovery() // không hại gì; một số ROM cần poke
        this.adapter.clear()
        val set = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner?.startScan(null, set, scanCb)
            txtStatus.text = "Đang quét… Bấm 1 nút trên remote"
        } catch (e: SecurityException) {
            Toast.makeText(this, "Thiếu quyền Bluetooth/Location", Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Không thể bắt đầu quét", Toast.LENGTH_LONG).show()
        }
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            val addr = d.address ?: return
            if (!adapter.contains(addr)) {
                this@MainActivity.adapter.add(d.name ?: "(Không tên)", addr)
            }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(0, it) }
        }
        override fun onScanFailed(errorCode: Int) {
            Toast.makeText(this@MainActivity, "Quét thất bại ($errorCode)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPick(addr: String, name: String) {
        try { scanner?.stopScan(scanCb) } catch (_: Throwable) {}
        Prefs.saveDevice(this, addr, name)
        updateStatus()
        Toast.makeText(this, "Đã chọn $name ($addr), đang kết nối…", Toast.LENGTH_SHORT).show()
        // Chỉ khởi động Service sau khi người dùng chọn thiết bị
        try {
            startForegroundService(Intent(this, BleM3Service::class.java).apply {
                action = BleM3Service.ACTION_CONNECT
                putExtra(BleM3Service.EXTRA_ADDR, addr)
            })
        } catch (_: Throwable) {
            Toast.makeText(this, "Không thể khởi động service", Toast.LENGTH_LONG).show()
        }
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
