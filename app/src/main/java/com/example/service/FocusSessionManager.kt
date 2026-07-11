package com.example.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SessionHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FocusSessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val lessonName: String = "",
    val durationMinutes: Int = 0,
    val remainingSeconds: Long = 0L,
    val pauseCount: Int = 0,
    val actualFocusTimeSeconds: Long = 0L,
    val startTimeMillis: Long = 0L,
    val blockedApps: Set<String> = emptySet(),
    val showFloatingWidget: Boolean = false,
    val lastTickTimeMillis: Long = 0L
)

class FocusSessionManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(appContext)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _sessionState = MutableStateFlow(FocusSessionState())
    val sessionState: StateFlow<FocusSessionState> = _sessionState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    init {
        restoreSession()
    }

    companion object {
        private const val TAG = "FocusSessionManager"
        @Volatile
        private var INSTANCE: FocusSessionManager? = null

        fun getInstance(context: Context): FocusSessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FocusSessionManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    private fun restoreSession() {
        val isActive = prefs.getBoolean("is_active", false)
        if (!isActive) {
            _sessionState.value = FocusSessionState()
            return
        }

        val isPaused = prefs.getBoolean("is_paused", false)
        val lessonName = prefs.getString("lesson_name", "") ?: ""
        val durationMinutes = prefs.getInt("duration_minutes", 0)
        val remainingSeconds = prefs.getLong("remaining_seconds", 0L)
        val pauseCount = prefs.getInt("pause_count", 0)
        val actualFocusTimeSeconds = prefs.getLong("actual_focus_time_seconds", 0L)
        val startTimeMillis = prefs.getLong("start_time_millis", 0L)
        val blockedAppsString = prefs.getString("blocked_apps", "") ?: ""
        val blockedApps = if (blockedAppsString.isEmpty()) emptySet() else blockedAppsString.split(",").toSet()
        val showFloatingWidget = prefs.getBoolean("show_floating_widget", false)
        val lastTickTimeMillis = prefs.getLong("last_tick_time_millis", 0L)

        var calculatedRemaining = remainingSeconds
        var calculatedActualFocus = actualFocusTimeSeconds

        // Calculate time elapsed since the last tick if not paused
        if (!isPaused && lastTickTimeMillis > 0L) {
            val elapsedMs = System.currentTimeMillis() - lastTickTimeMillis
            val elapsedSecs = elapsedMs / 1000
            if (elapsedSecs > 0) {
                calculatedRemaining = (remainingSeconds - elapsedSecs).coerceAtLeast(0L)
                calculatedActualFocus = actualFocusTimeSeconds + (remainingSeconds - calculatedRemaining)
            }
        }

        _sessionState.value = FocusSessionState(
            isActive = calculatedRemaining > 0,
            isPaused = isPaused,
            lessonName = lessonName,
            durationMinutes = durationMinutes,
            remainingSeconds = calculatedRemaining,
            pauseCount = pauseCount,
            actualFocusTimeSeconds = calculatedActualFocus,
            startTimeMillis = startTimeMillis,
            blockedApps = blockedApps,
            showFloatingWidget = showFloatingWidget,
            lastTickTimeMillis = System.currentTimeMillis()
        )

        if (calculatedRemaining > 0) {
            saveSessionToPrefs(_sessionState.value)
            if (!isPaused) {
                startTimerLoop()
                triggerForegroundService()
            }
        } else {
            // Session expired during offline/power off
            scope.launch {
                saveCompletedSession(isCompleted = true)
            }
            clearSession()
        }
    }

    fun startSession(lessonName: String, durationMinutes: Int, blockedApps: Set<String>, showWidget: Boolean) {
        val seconds = durationMinutes * 60L
        val state = FocusSessionState(
            isActive = true,
            isPaused = false,
            lessonName = lessonName,
            durationMinutes = durationMinutes,
            remainingSeconds = seconds,
            pauseCount = 0,
            actualFocusTimeSeconds = 0,
            startTimeMillis = System.currentTimeMillis(),
            blockedApps = blockedApps,
            showFloatingWidget = showWidget,
            lastTickTimeMillis = System.currentTimeMillis()
        )
        _sessionState.value = state
        saveSessionToPrefs(state)

        startTimerLoop()
        triggerForegroundService()
    }

    fun pauseSession() {
        val current = _sessionState.value
        if (!current.isActive || current.isPaused) return

        stopTimerLoop()

        val updated = current.copy(
            isPaused = true,
            pauseCount = current.pauseCount + 1,
            lastTickTimeMillis = System.currentTimeMillis()
        )
        _sessionState.value = updated
        saveSessionToPrefs(updated)
        triggerForegroundService()
    }

    fun resumeSession() {
        val current = _sessionState.value
        if (!current.isActive || !current.isPaused) return

        val updated = current.copy(
            isPaused = false,
            lastTickTimeMillis = System.currentTimeMillis()
        )
        _sessionState.value = updated
        saveSessionToPrefs(updated)

        startTimerLoop()
        triggerForegroundService()
    }

    fun endSession(isCancelled: Boolean) {
        val current = _sessionState.value
        if (!current.isActive) return

        stopTimerLoop()

        scope.launch {
            saveCompletedSession(isCompleted = !isCancelled)
        }

        clearSession()
        stopForegroundService()
    }

    private fun clearSession() {
        _sessionState.value = FocusSessionState()
        prefs.edit().clear().apply()
    }

    private fun saveSessionToPrefs(state: FocusSessionState) {
        prefs.edit().apply {
            putBoolean("is_active", state.isActive)
            putBoolean("is_paused", state.isPaused)
            putString("lesson_name", state.lessonName)
            putInt("duration_minutes", state.durationMinutes)
            putLong("remaining_seconds", state.remainingSeconds)
            putInt("pause_count", state.pauseCount)
            putLong("actual_focus_time_seconds", state.actualFocusTimeSeconds)
            putLong("start_time_millis", state.startTimeMillis)
            putString("blocked_apps", state.blockedApps.joinToString(","))
            putBoolean("show_floating_widget", state.showFloatingWidget)
            putLong("last_tick_time_millis", state.lastTickTimeMillis)
        }.apply()
    }

    private fun startTimerLoop() {
        stopTimerLoop()
        timerRunnable = object : Runnable {
            override fun run() {
                val current = _sessionState.value
                if (current.isActive && !current.isPaused) {
                    val newRemaining = current.remainingSeconds - 1
                    if (newRemaining <= 0) {
                        // Completed!
                        _sessionState.value = current.copy(
                            remainingSeconds = 0,
                            actualFocusTimeSeconds = current.actualFocusTimeSeconds + 1,
                            isActive = false
                        )
                        scope.launch {
                            saveCompletedSession(isCompleted = true)
                            clearSession()
                            stopForegroundService()
                        }
                    } else {
                        val updated = current.copy(
                            remainingSeconds = newRemaining,
                            actualFocusTimeSeconds = current.actualFocusTimeSeconds + 1,
                            lastTickTimeMillis = System.currentTimeMillis()
                        )
                        _sessionState.value = updated
                        saveSessionToPrefs(updated)
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }
        handler.postDelayed(timerRunnable!!, 1000)
    }

    private fun stopTimerLoop() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private suspend fun saveCompletedSession(isCompleted: Boolean) {
        val current = _sessionState.value
        if (current.lessonName.isNotEmpty()) {
            val history = SessionHistory(
                lessonName = current.lessonName,
                date = current.startTimeMillis,
                durationMinutes = current.durationMinutes,
                completed = isCompleted,
                cancelled = !isCompleted,
                actualFocusTimeSeconds = current.actualFocusTimeSeconds,
                pauseCount = current.pauseCount
            )
            db.sessionHistoryDao().insertHistory(history)
            Log.d(TAG, "Saved session history: $history")
        }
    }

    private fun triggerForegroundService() {
        val intent = Intent(appContext, FocusService::class.java).apply {
            action = FocusService.ACTION_UPDATE
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start/update foreground service", e)
        }
    }

    private fun stopForegroundService() {
        val intent = Intent(appContext, FocusService::class.java)
        appContext.stopService(intent)
    }

    // Toggle widget setting on-the-fly
    fun setShowFloatingWidget(show: Boolean) {
        val current = _sessionState.value
        if (current.isActive) {
            val updated = current.copy(showFloatingWidget = show)
            _sessionState.value = updated
            saveSessionToPrefs(updated)
            triggerForegroundService()
        }
    }
}
