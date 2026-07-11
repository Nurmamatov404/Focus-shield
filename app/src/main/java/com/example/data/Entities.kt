package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_history")
data class SessionHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lessonName: String,
    val date: Long, // timestamp
    val durationMinutes: Int,
    val completed: Boolean,
    val cancelled: Boolean,
    val actualFocusTimeSeconds: Long,
    val pauseCount: Int
)

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBlocked: Boolean = true
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
