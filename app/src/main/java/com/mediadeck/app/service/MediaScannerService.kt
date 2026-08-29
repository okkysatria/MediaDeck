package com.mediadeck.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mediadeck.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MediaScannerService : Service() {

    companion object {
        const val CHANNEL_ID = "MediaScannerChannel"
        const val NOTIFICATION_ID = 1001
        var isServiceRunning = false

        fun startService(context: Context, text: String = "Memindai Pustaka...") {
            if (!isServiceRunning) {
                val intent = Intent(context, MediaScannerService::class.java).apply {
                    putExtra("PROGRESS_TEXT", text)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    isServiceRunning = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val pendingIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("MediaDeck Scanner")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .build()
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, MediaScannerService::class.java)
            context.stopService(intent)
            isServiceRunning = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra("PROGRESS_TEXT") ?: "Memindai Pustaka..."
        val notification = createNotification(text)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
        isServiceRunning = false
    }

    private fun createNotification(content: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MediaDeck Scanner")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Scanner",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi saat memindai media pustaka"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
