package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.example.ui.FocusViewModel
import com.example.ui.FocusViewModelFactory
import com.example.ui.MainAppContainer
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FocusViewModel by viewModels {
        FocusViewModelFactory(applicationContext, (application as FocusApplication).repository)
    }

    private val initialBlockMessage = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial intent
        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val amoledMode by viewModel.amoledMode.collectAsState()

            val isDarkTheme = when (themeMode) {
                "Tungi" -> true
                "Kunduzgi" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                amoledMode = amoledMode
            ) {
                MainAppContainer(
                    viewModel = viewModel,
                    initialBlockMsg = initialBlockMessage.value,
                    onDismissBlock = {
                        initialBlockMessage.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d("MainActivity", "handleIntent action: ${intent?.action}")
        if (intent?.action == "com.example.action.SHOW_BLOCK_SCREEN") {
            val msg = intent.getStringExtra("extra_block_message")
            initialBlockMessage.value = msg ?: "Siz hozir darsdasiz. Chalg'imang!"
        } else {
            initialBlockMessage.value = null
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh installed apps checklist in case changes occurred in the background
        viewModel.refreshInstalledApps()
        viewModel.updatePermissionStatuses()
    }
}
