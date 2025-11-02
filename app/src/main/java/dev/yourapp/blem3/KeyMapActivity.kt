package dev.yourapp.blem3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class KeyMapActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private val keyMap by lazy { Prefs.loadMap(this) } // usage -> String (enum.name)

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
        }

        root.addView(TextView(this).apply {
            text = "Bấm nút trên remote để bắt mã, sau đó chọn hành động và nhấn Lưu."
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        root.addView(Button(this).apply {
            text = "Lưu"
            setOnClickListener {
                Prefs.saveMap(this@KeyMapActivity, keyMap)
                Toast.makeText(this@KeyMapActivity, "Đã lưu", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setContentView(root)

        registerReceiver(keyReceiver, IntentFilter("blem3.ACTION_KEY_RAW"))
    }

    override fun onDestroy() {
        unregisterReceiver(keyReceiver)
        super.onDestroy()
    }

    private fun addOrUpdateRow(usage: Int) {
        // nếu đã có thì thôi (đơn giản)
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? LinearLayout ?: continue
            val tv = row.getChildAt(0) as? TextView ?: continue
            if (tv.text.toString().equals("0x" + usage.toString(16).uppercase())) return
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL

            val tv = TextView(this@KeyMapActivity).apply {
                text = "0x" + usage.toString(16).uppercase()
                width = 220
            }

            val display = Action.values().map { it.name } // dùng enum từ Actions.kt
            val spinner = Spinner(this@KeyMapActivity).apply {
                adapter = ArrayAdapter(
                    this@KeyMapActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    display
                )
                val curName = keyMap[usage] ?: Action.NONE.name
                val idx = display.indexOf(curName).let { if (it >= 0) it else display.indexOf(Action.NONE.name) }
                setSelection(idx)
                onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                        keyMap[usage] = display[pos] // lưu enum.name
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            }

            addView(tv); addView(spinner)
        }
        list.addView(row)
    }
}
