package com.gojektracker.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.gojektracker.app.MainActivity
import com.gojektracker.app.viewmodel.MainViewModel

class OrderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val viewModel = MainViewModel.activeInstance

        when (action) {
            "com.gojektracker.app.ACTION_START_ORDER" -> {
                try {
                    viewModel?.startOrder()
                    Toast.makeText(context, "Merekam perjalanan: Menjemput penumpang...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            "com.gojektracker.app.ACTION_PICKUP" -> {
                try {
                    viewModel?.pickupOrder()
                    Toast.makeText(context, "Pickup dikonfirmasi! Menuju tempat antar.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            "com.gojektracker.app.ACTION_COMPLETE" -> {
                // Will be handled directly by MainActivity via PendingIntent.getActivity
            }

            "com.gojektracker.app.ACTION_CANCEL" -> {
                try {
                    viewModel?.cancelActiveOrder()
                    Toast.makeText(context, "Perekaman order dibatalkan.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Opens MainActivity and navigates to the given screen.
     * Uses FLAG_ACTIVITY_SINGLE_TOP so onNewIntent is called if the activity is already running.
     */
    private fun openApp(context: Context, navigateTo: String, showSaveDialog: Boolean) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", navigateTo)
            putExtra("SHOW_SAVE_DIALOG", showSaveDialog)
        }
        context.startActivity(launchIntent)
    }
}
