package com.mediadeck.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MediaDeckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val field = android.database.CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 5 * 1024 * 1024) 
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
