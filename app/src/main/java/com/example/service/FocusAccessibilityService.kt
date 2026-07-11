package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.MainActivity

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusAccessibility"
        var isServiceRunning = false
    }

    private lateinit var sessionManager: FocusSessionManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        isServiceRunning = true
        sessionManager = FocusSessionManager.getInstance(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val state = sessionManager.sessionState.value
        // Only block if session is active and not paused
        if (!state.isActive || state.isPaused) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""
        
        Log.d(TAG, "Package changed: $packageName, Class: $className")

        // 0. NEVER block our own application or system/launcher interfaces to prevent infinite loops
        val ourPackage = applicationContext.packageName
        val defaultLauncher = getDefaultLauncherPackage()
        if (packageName == ourPackage || 
            packageName == "com.example" || 
            packageName == defaultLauncher ||
            packageName == "com.android.launcher" || 
            packageName.contains("launcher") || 
            packageName.contains("systemui") ||
            packageName == "android"
        ) {
            return
        }

        // 1. Prevent App settings/uninstall manipulation during active session
        val isSettingsPackage = packageName == "com.android.settings" || 
                              packageName == "com.google.android.settings" ||
                              packageName.contains("settings")

        if (isSettingsPackage) {
            val eventText = event.text.joinToString(" ").lowercase()
            // Check if user is looking at our app settings or trying to manage accessibility services
            val containsOurAppName = eventText.contains("focus shield") || 
                                     eventText.contains("focus_shield") || 
                                     eventText.contains("com.aistudio.focusshield") ||
                                     eventText.contains("com.example") ||
                                     className.contains("InstalledAppDetails") ||
                                     className.contains("AppInfo")

            if (containsOurAppName) {
                // Redirect immediately to block uninstall / force stop
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockOverlayScreen("Dars davomida ilovani o'chirish yoki sozlamalarini o'zgartirish cheklangan!")
                return
            }
        }

        // 2. Block user specified apps
        if (state.blockedApps.contains(packageName)) {
            // Redirect immediately to Home screen to exit the blocked app
            performGlobalAction(GLOBAL_ACTION_HOME)
            // Start lock warning screen inside Focus Shield
            showBlockOverlayScreen("Siz hozir darsdasiz. Chalg'imang!")
        }
    }

    private fun showBlockOverlayScreen(message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = "com.example.action.SHOW_BLOCK_SCREEN"
            putExtra("extra_block_message", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch block MainActivity", e)
        }
    }

    private fun getDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }
}
