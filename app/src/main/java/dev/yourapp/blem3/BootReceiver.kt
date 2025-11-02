package dev.yourapp.blem3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.yourapp.blem3.Prefs.autoStart
import dev.yourapp.blem3.Prefs.savedAddr

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && context.autoStart && context.savedAddr != null) {
            context.startForegroundService(Intent(context, BleM3Service::class.java))
        }
    }
}
