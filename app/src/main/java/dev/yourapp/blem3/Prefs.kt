package dev.yourapp.blem3

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF = "blem3_prefs"
    private const val KEY_ADDR = "saved_addr"
    private const val KEY_NAME = "saved_name"
    private const val KEY_AUTOSTART = "auto_start"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun saveDevice(ctx: Context, addr: String, name: String?) {
        sp(ctx).edit().putString(KEY_ADDR, addr).putString(KEY_NAME, name ?: "").apply()
    }
    fun savedAddr(ctx: Context): String? = sp(ctx).getString(KEY_ADDR, null)
    fun savedName(ctx: Context): String? = sp(ctx).getString(KEY_NAME, null)

    fun setAutoStart(ctx: Context, on: Boolean) =
        sp(ctx).edit().putBoolean(KEY_AUTOSTART, on).apply()
    fun autoStart(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_AUTOSTART, true)
}
