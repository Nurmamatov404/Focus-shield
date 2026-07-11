package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.FocusRepository

class FocusApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { 
        FocusRepository(
            sessionHistoryDao = database.sessionHistoryDao(),
            blockedAppDao = database.blockedAppDao(),
            appSettingDao = database.appSettingDao()
        ) 
    }
}
