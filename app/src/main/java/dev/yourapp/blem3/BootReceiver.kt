package dev.yourapp.blem3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.autoStart(context)) {
            val i = Intent(context, BleM3Service::class.java).apply {
                action = BleM3Service.ACTION_CONNECT
            }
            try { context.startForegroundService(i) } catch (_: Throwable) {}
        }
    }
}
