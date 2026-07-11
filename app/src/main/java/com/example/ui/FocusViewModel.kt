package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BlockedApp
import com.example.data.FocusRepository
import com.example.data.SessionHistory
import com.example.service.FocusSessionManager
import com.example.service.FocusSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean = false
)

class FocusViewModel(
    private val context: Context,
    private val repository: FocusRepository
) : ViewModel() {

    private val sessionManager = FocusSessionManager.getInstance(context)

    // Active session state from service
    val sessionState: StateFlow<FocusSessionState> = sessionManager.sessionState

    // Installed launcher applications
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Query for app search
    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    // Filtered launcher applications based on search query
    val filteredApps: StateFlow<List<AppInfo>> = combine(_installedApps, _appSearchQuery) { apps, query ->
        if (query.isEmpty()) {
            apps
        } else {
            apps.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Database session history
    val sessionHistory: StateFlow<List<SessionHistory>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings flows
    private val _themeMode = MutableStateFlow("Tizim") // Tizim, Tungi, Kunduzgi
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _amoledMode = MutableStateFlow(false)
    val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()

    private val _widgetTransparency = MutableStateFlow(85f)
    val widgetTransparency: StateFlow<Float> = _widgetTransparency.asStateFlow()

    private val _widgetSize = MutableStateFlow("O'rtacha") // Kichik, O'rtacha, Katta
    val widgetSize: StateFlow<String> = _widgetSize.asStateFlow()

    private val _notificationSound = MutableStateFlow(true)
    val notificationSound: StateFlow<Boolean> = _notificationSound.asStateFlow()

    private val _dailyGoalMinutes = MutableStateFlow(120)
    val dailyGoalMinutes: StateFlow<Int> = _dailyGoalMinutes.asStateFlow()

    private val _rebootAutoStart = MutableStateFlow(true)
    val rebootAutoStart: StateFlow<Boolean> = _rebootAutoStart.asStateFlow()

    init {
        loadSettings()
        refreshInstalledApps()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val sp = context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
            _themeMode.value = sp.getString("theme_mode", "Tizim") ?: "Tizim"
            _amoledMode.value = sp.getBoolean("amoled_mode", false)
            _widgetTransparency.value = sp.getFloat("widget_transparency_val", 85f)
            _widgetSize.value = sp.getString("widget_size", "O'rtacha") ?: "O'rtacha"
            _notificationSound.value = sp.getBoolean("notification_sound", true)
            _dailyGoalMinutes.value = sp.getInt("daily_goal_minutes", 120)
            _rebootAutoStart.value = sp.getBoolean("reboot_auto_start", true)
        }
    }

    fun saveThemeMode(mode: String) {
        _themeMode.value = mode
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", mode).apply()
    }

    fun saveAmoledMode(enabled: Boolean) {
        _amoledMode.value = enabled
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("amoled_mode", enabled).apply()
    }

    fun saveWidgetTransparency(value: Float) {
        _widgetTransparency.value = value
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("widget_transparency_val", value)
            .putString("widget_transparency", value.toInt().toString()).apply()
        // Notify service
        sessionManager.setShowFloatingWidget(sessionState.value.showFloatingWidget)
    }

    fun saveWidgetSize(size: String) {
        _widgetSize.value = size
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE).edit()
            .putString("widget_size", size).apply()
        // Notify service
        sessionManager.setShowFloatingWidget(sessionState.value.showFloatingWidget)
    }

    fun saveNotificationSound(enabled: Boolean) {
        _notificationSound.value = enabled
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("notification_sound", enabled).apply()
    }

    fun saveDailyGoalMinutes(minutes: Int) {
        _dailyGoalMinutes.value = minutes
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE).edit()
            .putInt("daily_goal_minutes", minutes).apply()
    }

    fun saveRebootAutoStart(enabled: Boolean) {
        _rebootAutoStart.value = enabled
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("reboot_auto_start", enabled).apply()
    }

    fun updateSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                getInstalledLauncherApps(context)
            }
            // Fetch already selected blocked apps from Room
            val blockedApps = repository.getBlockedAppsSync().associateBy { it.packageName }
            
            _installedApps.value = list.map { app ->
                app.copy(isSelected = blockedApps.containsKey(app.packageName))
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        viewModelScope.launch {
            val currentList = _installedApps.value.toMutableList()
            val index = currentList.indexOfFirst { it.packageName == packageName }
            if (index != -1) {
                val app = currentList[index]
                val updatedApp = app.copy(isSelected = !app.isSelected)
                currentList[index] = updatedApp
                _installedApps.value = currentList

                // Persist selection into Room
                if (updatedApp.isSelected) {
                    repository.insertBlockedApp(BlockedApp(packageName = app.packageName, appName = app.appName, isBlocked = true))
                } else {
                    repository.deleteBlockedApp(app.packageName)
                }
            }
        }
    }

    fun selectAllApps(select: Boolean) {
        viewModelScope.launch {
            val currentList = _installedApps.value.map { it.copy(isSelected = select) }
            _installedApps.value = currentList
            
            if (select) {
                val blocked = currentList.map { BlockedApp(packageName = it.packageName, appName = it.appName, isBlocked = true) }
                repository.insertBlockedApps(blocked)
            } else {
                repository.clearAllBlockedApps()
            }
        }
    }

    private fun getInstalledLauncherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val list = pm.queryIntentActivities(intent, 0)
        val appList = mutableListOf<AppInfo>()
        val ourPackage = context.packageName

        for (resolveInfo in list) {
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName == ourPackage) continue
            val appName = resolveInfo.loadLabel(pm).toString()
            appList.add(AppInfo(packageName, appName))
        }
        return appList.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    fun startFocusSession(lessonName: String, durationMinutes: Int, showWidget: Boolean) {
        viewModelScope.launch {
            val blocked = repository.getBlockedAppsSync().map { it.packageName }.toSet()
            sessionManager.startSession(
                lessonName = if (lessonName.trim().isEmpty()) "Dars" else lessonName.trim(),
                durationMinutes = durationMinutes,
                blockedApps = blocked,
                showWidget = showWidget
            )
        }
    }

    fun pauseFocusSession() = sessionManager.pauseSession()

    fun resumeFocusSession() = sessionManager.resumeSession()

    fun endFocusSession(isCancelled: Boolean) = sessionManager.endSession(isCancelled = isCancelled)

    fun toggleFloatingWidget(show: Boolean) {
        sessionManager.setShowFloatingWidget(show)
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    // --- CSV Backup and Restore ---

    fun exportHistoryToCSV(uri: Uri): Boolean {
        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                val writer = outputStream.bufferedWriter()
                // CSV Header
                writer.write("id,lessonName,date,durationMinutes,completed,cancelled,actualFocusTimeSeconds,pauseCount\n")
                
                val history = sessionHistory.value
                for (item in history) {
                    writer.write("${item.id},\"${item.lessonName.replace("\"", "\"\"")}\",${item.date},${item.durationMinutes},${item.completed},${item.cancelled},${item.actualFocusTimeSeconds},${item.pauseCount}\n")
                }
                writer.flush()
                writer.close()
                outputStream.close()
                true
            } else false
        } catch (e: Exception) {
            Log.e("FocusViewModel", "Error exporting history", e)
            false
        }
    }

    fun importHistoryFromCSV(uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val reader = BufferedReader(InputStreamReader(inputStream))
                val header = reader.readLine() // skip header
                
                var line: String? = reader.readLine()
                val importedList = mutableListOf<SessionHistory>()

                while (line != null) {
                    val tokens = line.split(",")
                    if (tokens.size >= 8) {
                        val lessonName = tokens[1].replace("\"", "")
                        val date = tokens[2].toLongOrNull() ?: System.currentTimeMillis()
                        val durationMinutes = tokens[3].toIntOrNull() ?: 30
                        val completed = tokens[4].toBoolean()
                        val cancelled = tokens[5].toBoolean()
                        val actualFocusTimeSeconds = tokens[6].toLongOrNull() ?: 0L
                        val pauseCount = tokens[7].toIntOrNull() ?: 0
                        
                        importedList.add(
                            SessionHistory(
                                lessonName = lessonName,
                                date = date,
                                durationMinutes = durationMinutes,
                                completed = completed,
                                cancelled = cancelled,
                                actualFocusTimeSeconds = actualFocusTimeSeconds,
                                pauseCount = pauseCount
                            )
                        )
                    }
                    line = reader.readLine()
                }
                reader.close()
                inputStream.close()

                viewModelScope.launch {
                    for (item in importedList) {
                        repository.insertHistory(item)
                    }
                }
                true
            } else false
        } catch (e: Exception) {
            Log.e("FocusViewModel", "Error importing history", e)
            false
        }
    }
}

class FocusViewModelFactory(
    private val context: Context,
    private val repository: FocusRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            return FocusViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
