package dev.yourapp.blem3

import android.content.Context
import org.json.JSONObject

object Prefs {
    private const val NAME = "blem3_prefs"
    private const val KEY_DEVICE_ADDR = "device_addr"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_AUTOSTART   = "autostart"
    private const val KEY_MAP         = "keymap" // JSON: usage(int) -> action label

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // Thiết bị đã lưu
    fun savedAddr(ctx: Context): String? = sp(ctx).getString(KEY_DEVICE_ADDR, null)
    fun savedName(ctx: Context): String? = sp(ctx).getString(KEY_DEVICE_NAME, null)
    fun saveDevice(ctx: Context, addr: String, name: String?) {
        sp(ctx).edit().putString(KEY_DEVICE_ADDR, addr)
            .putString(KEY_DEVICE_NAME, name ?: "")
            .apply()
    }

    // Autostart
    fun autoStart(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_AUTOSTART, true)
    fun setAutoStart(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean(KEY_AUTOSTART, on).apply()
    }

    // Key map: usage -> action label
    fun saveMap(ctx: Context, map: Map<Int, String>) {
        val jo = JSONObject()
        map.forEach { (k,v) -> jo.put(k.toString(), v) }
        sp(ctx).edit().putString(KEY_MAP, jo.toString()).apply()
    }

    fun loadMap(ctx: Context): MutableMap<Int,String> {
        val s = sp(ctx).getString(KEY_MAP, "{}") ?: "{}"
        val jo = JSONObject(s)
        val out = mutableMapOf<Int,String>()
        val it = jo.keys()
        while (it.hasNext()) {
            val k = it.next()
            out[k.toInt()] = jo.optString(k, "")
        }
        return out
    }
}
