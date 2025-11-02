package dev.yourapp.blem3

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("BLE-M3", "CRASH in ${t.name}", e)
        }
    }
}
