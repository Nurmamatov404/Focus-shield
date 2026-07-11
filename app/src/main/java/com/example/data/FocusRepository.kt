package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FocusRepository(
    private val sessionHistoryDao: SessionHistoryDao,
    private val blockedAppDao: BlockedAppDao,
    private val appSettingDao: AppSettingDao
) {
    val allHistory: Flow<List<SessionHistory>> = sessionHistoryDao.getAllHistory()
    val allBlockedApps: Flow<List<BlockedApp>> = blockedAppDao.getAllBlockedApps()
    val allSettings: Flow<Map<String, String>> = appSettingDao.getAllSettingsFlow().map { list ->
        list.associate { it.key to it.value }
    }

    suspend fun insertHistory(history: SessionHistory) {
        sessionHistoryDao.insertHistory(history)
    }

    suspend fun deleteHistory(id: Int) {
        sessionHistoryDao.deleteHistory(id)
    }

    suspend fun clearAllHistory() {
        sessionHistoryDao.clearAllHistory()
    }

    suspend fun getBlockedAppsSync(): List<BlockedApp> {
        return blockedAppDao.getBlockedAppsSync()
    }

    suspend fun insertBlockedApp(app: BlockedApp) {
        blockedAppDao.insertBlockedApp(app)
    }

    suspend fun insertBlockedApps(apps: List<BlockedApp>) {
        blockedAppDao.insertBlockedApps(apps)
    }

    suspend fun deleteBlockedApp(packageName: String) {
        blockedAppDao.deleteBlockedApp(packageName)
    }

    suspend fun clearAllBlockedApps() {
        blockedAppDao.clearAllBlockedApps()
    }

    suspend fun getSetting(key: String, defaultValue: String): String {
        return appSettingDao.getSettingValue(key) ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        appSettingDao.insertSetting(AppSetting(key, value))
    }
}
