package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed received. Restoring session if any...")
            // Just instantiating FocusSessionManager will invoke its restore logic
            try {
                FocusSessionManager.getInstance(context)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error restoring session on boot", e)
            }
        }
    }
}
