package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionHistoryDao {
    @Query("SELECT * FROM session_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<SessionHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SessionHistory)

    @Query("DELETE FROM session_history WHERE id = :id")
    suspend fun deleteHistory(id: Int)

    @Query("DELETE FROM session_history")
    suspend fun clearAllHistory()
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps WHERE isBlocked = 1")
    suspend fun getBlockedAppsSync(): List<BlockedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(app: BlockedApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApps(apps: List<BlockedApp>)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteBlockedApp(packageName: String)

    @Query("DELETE FROM blocked_apps")
    suspend fun clearAllBlockedApps()
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    fun getAllSettingsFlow(): Flow<List<AppSetting>>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)
}
