package com.gojektracker.app.ui.components

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gojektracker.app.MainActivity
import com.gojektracker.app.viewmodel.WorkflowState
import java.util.Locale

object OrderNotificationManager {
    private const val CHANNEL_ID = "gojek_tracker_channel"
    private const val NOTIFICATION_ID = 8827

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Aktivitas Driver Gojek"
            val descriptionText = "Notifikasi status perjalanan dan pencatatan order Gojek"
            val importance = NotificationManager.IMPORTANCE_LOW // Low to keep updates silent and uncluttered
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTrackingNotification(
        context: Context,
        state: WorkflowState,
        distanceKm: Double,
        durationMinutes: Long,
        currentAddress: String = ""
    ) {
        createNotificationChannel(context)

        // Open MainActivity when notification body is clicked
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Intents targeting our OrderActionReceiver for background updates (does not bring app to foreground)
        val startIntent = Intent("com.gojektracker.app.ACTION_START_ORDER").apply { `package` = context.packageName }
        val startPending = PendingIntent.getBroadcast(
            context, 1, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pickupIntent = Intent("com.gojektracker.app.ACTION_PICKUP").apply { `package` = context.packageName }
        val pickupPending = PendingIntent.getBroadcast(
            context, 2, pickupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Selesaikan Order directly targets MainActivity to open app foreground
        val completeIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.gojektracker.app.ACTION_COMPLETE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "rekam_order")
            putExtra("SHOW_SAVE_DIALOG", true)
        }
        val completePending = PendingIntent.getActivity(
            context, 3, completeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent("com.gojektracker.app.ACTION_CANCEL").apply { `package` = context.packageName }
        val cancelPending = PendingIntent.getBroadcast(
            context, 4, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val content: String
        
        // Build base notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions) // Guaranteed system resource
            .setContentIntent(openAppPendingIntent)
            .setOngoing(state != WorkflowState.IDLE)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        when (state) {
            WorkflowState.IDLE -> {
                title = "Gojek Tracker • Siap Merekam"
                content = "Sistem standby. Mulai berkendara & rekam trip kamu di sini!"
                builder.addAction(android.R.drawable.ic_media_play, "Mulai Order", startPending)
            }
            WorkflowState.STARTED -> {
                title = "Gojek Tracker • Menuju Lokasi"
                val distStr = String.format(Locale.US, "%.2f km", distanceKm)
                content = "Perjalanan: $distStr • Waktu: $durationMinutes mnt"
                builder.addAction(android.R.drawable.ic_menu_myplaces, "Konfirmasi Pickup", pickupPending)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelPending)
            }
            WorkflowState.PICKED_UP -> {
                title = "Gojek Tracker • Mengantar Layanan"
                val distStr = String.format(Locale.US, "%.2f km", distanceKm)
                content = "Jarak: $distStr • Waktu: $durationMinutes mnt"
                builder.addAction(android.R.drawable.ic_menu_compass, "Selesaikan Order", completePending)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelPending)
            }
        }

        val footerText = if (currentAddress.isNotEmpty() && state != WorkflowState.IDLE) {
            "$content\nLokasi saat ini: $currentAddress"
        } else {
            content
        }

        builder.setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(footerText))

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
