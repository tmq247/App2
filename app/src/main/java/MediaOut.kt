package dev.yourapp.blem3

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

class MediaOut(private val ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun fire(code: Int) {
        val now = System.currentTimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP,   code, 0))
    }
    fun playPause() = fire(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next()      = fire(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prev()      = fire(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun volUp()     = fire(KeyEvent.KEYCODE_VOLUME_UP)
    fun volDown()   = fire(KeyEvent.KEYCODE_VOLUME_DOWN)

    // Zello toggle (nếu cài Zello)
    fun zelloToggle() {
        val i = android.content.Intent("com.loudtalks.intent.action.TOGGLE_TALK")
        i.setPackage("com.loudtalks")
        ctx.sendBroadcast(i)
    }
}
