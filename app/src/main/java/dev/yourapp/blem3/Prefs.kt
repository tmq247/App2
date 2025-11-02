package dev.yourapp.blem3

import android.content.Context
import org.json.JSONObject

object Prefs {
    private const val NAME = "blem3_prefs"
    private const val KEY_DEVICE_ADDR = "device_addr"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_AUTOSTART   = "autostart"
    private const val KEY_MAP         = "keymap" // JSON: usage -> action label

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var Context.savedAddr: String?
        get() = sp(this).getString(KEY_DEVICE_ADDR, null)
        set(v) { sp(this).edit().putString(KEY_DEVICE_ADDR, v).apply() }

    var Context.savedName: String?
        get() = sp(this).getString(KEY_DEVICE_NAME, null)
        set(v) { sp(this).edit().putString(KEY_DEVICE_NAME, v).apply() }

    var Context.autoStart: Boolean
        get() = sp(this).getBoolean(KEY_AUTOSTART, true)
        set(v) { sp(this).edit().putBoolean(KEY_AUTOSTART, v).apply() }

    fun saveMap(ctx: Context, map: Map<Int, String>) {
        val jo = JSONObject()
        map.forEach { (k,v) -> jo.put(k.toString(), v) }
        sp(ctx).edit().putString(KEY_MAP, jo.toString()).apply()
    }

    fun loadMap(ctx: Context): MutableMap<Int,String> {
        val s = sp(ctx).getString(KEY_MAP, "{}") ?: "{}"
        val jo = JSONObject(s)
        val out = mutableMapOf<Int,String>()
        jo.keys().forEach { k -> out[k.toInt()] = jo.getString(k) }
        return out
    }
}
