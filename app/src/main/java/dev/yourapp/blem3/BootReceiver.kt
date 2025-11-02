package dev.yourapp.blem3
import android.content.*

class BootReceiver: BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
      ctx.startForegroundService(Intent(ctx, BleM3Service::class.java))
    }
  }
}
