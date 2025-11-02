package dev.yourapp.blem3

import android.content.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class KeyMapActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private val keyMap by lazy { Prefs.loadMap(this) }

    private val keyReceiver = object: BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == "blem3.ACTION_KEY_RAW") {
                val usage = i.getIntExtra("usage", -1)
                if (usage != -1) addOrUpdateRow(usage)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,24,24,24)
            addView(TextView(this@KeyMapActivity).apply {
                text = "Bấm nút trên remote để bắt mã, sau đó chọn hành động."
            })
            list = LinearLayout(this@KeyMapActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(list)
            addView(Button(this@KeyMapActivity).apply {
                text = "Lưu"
                setOnClickListener {
                    Prefs.saveMap(this@KeyMapActivity, keyMap)
                    Toast.makeText(this@KeyMapActivity, "Đã lưu", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
        }
        setContentView(root)
        registerReceiver(keyReceiver, IntentFilter("blem3.ACTION_KEY_RAW"))
    }

    override fun onDestroy() {
        unregisterReceiver(keyReceiver)
        super.onDestroy()
    }

    private fun addOrUpdateRow(usage: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val tv = TextView(this@KeyMapActivity).apply {
                text = "0x" + usage.toString(16).uppercase()
                width = 220
            }
            val spinner = Spinner(this@KeyMapActivity).apply {
                adapter = ArrayAdapter(
                    this@KeyMapActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    Action.values().map { it.label }
                )
                val cur = keyMap[usage]?.let { Action.fromLabel(it) } ?: Action.NONE
                setSelection(Action.values().indexOf(cur))
                onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                        keyMap[usage] = Action.values()[pos].label
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            }
            addView(tv); addView(spinner)
        }
        list.addView(row)
    }
}
