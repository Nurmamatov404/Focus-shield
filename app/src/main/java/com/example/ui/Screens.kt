package com.example.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SessionHistory
import com.example.service.FocusAccessibilityService
import com.example.service.FocusSessionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    viewModel: FocusViewModel,
    initialBlockMsg: String?,
    onDismissBlock: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionState by viewModel.sessionState.collectAsState()
    
    // State to toggle navigation bar
    var selectedTab by remember { mutableStateOf(0) } // 0: Home, 1: Stats, 2: Settings

    // Check if we need to show the full-screen Block Lock Screen (if intent requested it or active session is currently blocked)
    var showBlockScreenMessage by remember { mutableStateOf<String?>(initialBlockMsg) }

    // Synchronize intent block messages on activity callbacks
    LaunchedEffect(initialBlockMsg) {
        if (initialBlockMsg != null) {
            showBlockScreenMessage = initialBlockMsg
        }
    }

    if (showBlockScreenMessage != null && sessionState.isActive && !sessionState.isPaused) {
        BlockedLockScreen(
            state = sessionState,
            message = showBlockScreenMessage ?: "Siz hozir darsdasiz. Chalg'imang!",
            onBack = {
                showBlockScreenMessage = null
                onDismissBlock()
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                if (!sessionState.isActive) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Dars") },
                            label = { Text("Dars") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.BarChart, contentDescription = "Statistika") },
                            label = { Text("Tahlillar") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Sozlamalar") },
                            label = { Text("Sozlamalar") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                if (sessionState.isActive) {
                    ActiveSessionScreen(viewModel = viewModel, state = sessionState)
                } else {
                    when (selectedTab) {
                        0 -> HomeTab(viewModel = viewModel)
                        1 -> StatsTab(viewModel = viewModel)
                        2 -> SettingsTab(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun SleekSuggestionChip(
    onClick: () -> Unit,
    label: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun SleekDurationChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        )
    }
}

// --- TAB 1: HOME TAB ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(viewModel: FocusViewModel) {
    val context = LocalContext.current
    var lessonName by remember { mutableStateOf("") }
    
    // Duration in minutes
    var selectedDurationMinutes by remember { mutableStateOf(30) }
    var customDurationVal by remember { mutableStateOf(45f) }
    var isCustomDurationSelected by remember { mutableStateOf(false) }

    val blockedApps by viewModel.installedApps.collectAsState()
    val blockedCount = remember(blockedApps) { blockedApps.count { it.isSelected } }

    var showAppSelectionSheet by remember { mutableStateOf(false) }
    var showWidgetOverlayToggle by remember { mutableStateOf(true) }

    // Quick suggestions
    val suggestions = listOf("Matematika", "Python", "IELTS", "Physics", "Reading", "Coding")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Focus Shield",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                    )
                    Text(
                        text = "Chalg'ituvchi ilovalardan himoyalanish",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Stats Quick View
        item {
            val history by viewModel.sessionHistory.collectAsState()
            val todayFocusSeconds = remember(history) {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val todayStart = calendar.timeInMillis
                history.filter { it.date >= todayStart }.sumOf { it.actualFocusTimeSeconds }
            }
            
            val h = todayFocusSeconds / 3600
            val m = (todayFocusSeconds % 3600) / 60
            val todayString = if (h > 0) {
                "$h soat $m daqiqa"
            } else {
                "$m daqiqa"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "BUGUNGI NATIJA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = todayString,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Card 1: Lesson Name
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DARS NOMI",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = lessonName,
                        onValueChange = { lessonName = it },
                        placeholder = { Text("Masalan: Matematika, Coding...") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lesson_name_input"),
                        trailingIcon = {
                            if (lessonName.isNotEmpty()) {
                                IconButton(onClick = { lessonName = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Tozalash")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Suggestion row
                    Text(
                        text = "TEZKOR TANLASH:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.take(3).forEach { suggest ->
                            SleekSuggestionChip(
                                onClick = { lessonName = suggest },
                                label = suggest
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.drop(3).forEach { suggest ->
                            SleekSuggestionChip(
                                onClick = { lessonName = suggest },
                                label = suggest
                            )
                        }
                    }
                }
            }
        }

        // Card 2: Duration Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DAVOMIYLIGI",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Built-in presets
                    val presets = listOf(30, 45, 60, 120, 180)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.take(3).forEach { mins ->
                            val text = if (mins >= 60) "${mins / 60} soat" else "$mins min"
                            SleekDurationChip(
                                selected = !isCustomDurationSelected && selectedDurationMinutes == mins,
                                onClick = {
                                    isCustomDurationSelected = false
                                    selectedDurationMinutes = mins
                                },
                                label = text,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.drop(3).forEach { mins ->
                            val text = "${mins / 60} soat"
                            SleekDurationChip(
                                selected = !isCustomDurationSelected && selectedDurationMinutes == mins,
                                onClick = {
                                    isCustomDurationSelected = false
                                    selectedDurationMinutes = mins
                                },
                                label = text,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SleekDurationChip(
                            selected = isCustomDurationSelected,
                            onClick = { isCustomDurationSelected = true },
                            label = "Boshqa",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isCustomDurationSelected) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tanlangan vaqt: ${customDurationVal.toInt()} daqiqa (${String.format("%.1f", customDurationVal / 60f)} soat)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = customDurationVal,
                            onValueChange = { customDurationVal = it },
                            valueRange = 5f..360f,
                            steps = 71 // Increments of 5 minutes
                        )
                    }
                }
            }
        }

        // Card 3: Selected Apps Count
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BLOKLANADIGAN ILOVALAR",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (blockedCount == 0) "Ilova tanlanmagan (Bloklanmaydi)" else "$blockedCount ta ilova tanlandi",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (blockedCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = { showAppSelectionSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("select_apps_button")
                    ) {
                        Text("Tanlash")
                    }
                }
            }
        }

        // Options Toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Suzib yuruvchi taymer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Ekranda kichik dars nazorati vidjetini ko'rsatish",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showWidgetOverlayToggle,
                        onCheckedChange = { showWidgetOverlayToggle = it }
                    )
                }
            }
        }

        // Permission quick status warning (Only shown if permissions are missing)
        item {
            val isAccessibilityOn = remember { FocusAccessibilityService.isServiceRunning }
            val isOverlayOn = remember { Settings.canDrawOverlays(context) }
            
            if (!isAccessibilityOn || (showWidgetOverlayToggle && !isOverlayOn)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Diqqat",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "Muhim ruxsatnomalar yetishmayapti",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Bloklash ishlashi uchun Sozlamalar bo'limidan maxsus imkoniyatlarni va oyna ruxsatini yoqing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // BIG START BUTTON
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val finalDuration = if (isCustomDurationSelected) customDurationVal.toInt() else selectedDurationMinutes
                        viewModel.startFocusSession(
                            lessonName = if (lessonName.isBlank()) "Mobil Ilovalar" else lessonName,
                            durationMinutes = finalDuration,
                            showWidget = showWidgetOverlayToggle
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("start_session_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Boshlash",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SESSIYANI BOSHLASH",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                val isAccessibilityOn = remember { FocusAccessibilityService.isServiceRunning }
                Text(
                    text = if (isAccessibilityOn) "MAXSUS IMKONIYATLAR XIZMATI YOQILGAN" else "MAXSUS IMKONIYATLAR XIZMATI O'CHIRILGAN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = if (isAccessibilityOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Fullscreen BottomSheet or Dialog for App selection
    if (showAppSelectionSheet) {
        AppSelectionDialog(
            viewModel = viewModel,
            onClose = {
                showAppSelectionSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionDialog(viewModel: FocusViewModel, onClose: () -> Unit) {
    val query by viewModel.appSearchQuery.collectAsState()
    val apps by viewModel.filteredApps.collectAsState()
    val totalApps by viewModel.installedApps.collectAsState()
    val selectAllChecked = remember(totalApps) { totalApps.isNotEmpty() && totalApps.all { it.isSelected } }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxHeight(0.9f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ilovalarni Tanlash",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Yopish")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Ilova nomi bo'yicha qidirish...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Qidirish") },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Tozalash")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Select All Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectAllApps(!selectAllChecked) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectAllChecked,
                        onCheckedChange = { viewModel.selectAllApps(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Barcha ilovalarni tanlash",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Divider()

                // Apps List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleAppSelection(app.packageName) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Render App icon asynchronously
                            AppIconView(
                                packageName = app.packageName,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Checkbox(
                                checked = app.isSelected,
                                onCheckedChange = { viewModel.toggleAppSelection(app.packageName) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Done Button
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tayyor")
                }
            }
        }
    }
}

@Composable
fun AppIconView(packageName: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                try {
                    val icon = ctx.packageManager.getApplicationIcon(packageName)
                    setImageDrawable(icon)
                } catch (e: Exception) {
                    setImageResource(android.R.drawable.sym_def_app_icon)
                }
            }
        },
        modifier = modifier
    )
}

// --- ACTIVE SESSION OVERLAY / SCREEN ---

@Composable
fun ActiveSessionScreen(viewModel: FocusViewModel, state: FocusSessionState) {
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Pulse animation for central circle decoration
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Calculate percentage remaining for circular progress
    val totalSeconds = state.durationMinutes * 60f
    val elapsedSeconds = totalSeconds - state.remainingSeconds
    val progressFraction = if (totalSeconds > 0) (state.remainingSeconds / totalSeconds) else 1f

    if (showCancelConfirm) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Diqqat",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Darsni tugatasizmi?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Session yakunlanmagan. Hozir tugatsangiz foydali dars vaqti chala saqlanadi va chalg'igan hisoblanasiz.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCancelConfirm = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Davom etish")
                        }
                        Button(
                            onClick = {
                                showCancelConfirm = false
                                viewModel.endFocusSession(isCancelled = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tugatish")
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isPaused) Color.Yellow.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (state.isPaused) Color.Yellow else MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    text = state.lessonName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (state.isPaused) "Dars to'xtatildi (Pauza)" else "Diqqat jamlangan! Chalg'imang",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Circular Countdown Clock with custom Canvas drawings
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background shadow pulsing circles
                if (!state.isPaused) {
                    Box(
                        modifier = Modifier
                            .size(240.dp * pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    )
                }

                // Circle boundary Canvas
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Track circle
                    drawCircle(
                        color = trackColor,
                        radius = size.minDimension / 2 - 12,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Active sweep arc
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progressFraction,
                        useCenter = false,
                        topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
                        size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Text metrics
                val h = state.remainingSeconds / 3600
                val m = (state.remainingSeconds % 3600) / 60
                val s = state.remainingSeconds % 60
                val formattedTime = if (h > 0) {
                    String.format("%02d:%02d:%02d", h, m, s)
                } else {
                    String.format("%02d:%02d", m, s)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "QOLGAN VAQT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Quick Floating Widget toggle inside active session screen
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { viewModel.toggleFloatingWidget(!state.showFloatingWidget) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (state.showFloatingWidget) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Vidjet",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (state.showFloatingWidget) "Suzuvchi vidjet yoqilgan" else "Suzuvchi vidjet o'chirilgan",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Control Buttons: Pause, Resume, Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Action: Stop/End
                FilledTonalButton(
                    onClick = {
                        // If it is finished or remains 0, end without dialog
                        if (state.remainingSeconds <= 0) {
                            viewModel.endFocusSession(isCancelled = false)
                        } else {
                            showCancelConfirm = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Tugatish", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tugatish", color = MaterialTheme.colorScheme.error)
                }

                // Primary Action: Pause/Play
                Button(
                    onClick = {
                        if (state.isPaused) viewModel.resumeFocusSession() else viewModel.pauseFocusSession()
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.isPaused) "Davom ettirish" else "Pauza"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (state.isPaused) "Davom etish" else "Pauza")
                }
            }
        }
    }
}

// --- TAB 2: STATS TAB & HISTORY ---

@Composable
fun StatsTab(viewModel: FocusViewModel) {
    val history by viewModel.sessionHistory.collectAsState()

    // Calculate statistical metrics
    val totalFocusSeconds = remember(history) { history.sumOf { it.actualFocusTimeSeconds } }
    val longestSessionMinutes = remember(history) {
        if (history.isEmpty()) 0 
        else history.maxOf { it.actualFocusTimeSeconds } / 60
    }
    
    val completionRate = remember(history) {
        if (history.isEmpty()) 100
        else {
            val completed = history.count { it.completed }
            (completed * 100) / history.size
        }
    }

    // Calculate streaks (consecutive days of focus)
    val currentStreak = remember(history) { calculateCurrentStreak(history) }
    val bestStreak = remember(history) { calculateBestStreak(history) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Tahlillar va Statistika",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Metrics Grid (Today/Streak/Longest/Completion)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        title = "Jami Diqqat Vaqti",
                        value = formatFocusTime(totalFocusSeconds),
                        icon = Icons.Default.Timer,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Kundalik Seriya",
                        value = "$currentStreak Kun 🔥",
                        subtitle = "Eng zo'ri: $bestStreak kun",
                        icon = Icons.Default.LocalFireDepartment,
                        color = Color(0xFFFF5722),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        title = "Eng Uzun Dars",
                        value = "$longestSessionMinutes min",
                        icon = Icons.Default.TrendingUp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Bajarilish Foizi",
                        value = "$completionRate%",
                        subtitle = "Jami darslar: ${history.size}",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Canvas Custom Material Bar Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Haftalik Diqqat Grafigi (daqiqiqa)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    WeeklyBarChart(history = history)
                }
            }
        }

        // History list title
        item {
            Text(
                text = "Darslar Tarixi",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // History logs
        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HistoryToggleOff,
                            contentDescription = "Tarix bo'sh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Hali hech qanday dars seansi o'tkazilmadi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(history, key = { it.id }) { item ->
                HistoryItemRow(item = item, onDelete = { viewModel.deleteHistoryItem(item.id) })
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeeklyBarChart(history: List<SessionHistory>) {
    val context = LocalContext.current
    val barColor = MaterialTheme.colorScheme.primary
    val barTrackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    // Generate last 7 days focus time metrics
    val chartData = remember(history) {
        val calendar = Calendar.getInstance()
        val dataList = mutableListOf<Pair<String, Float>>() // Day string to Focus minutes
        val sdf = SimpleDateFormat("EE", Locale("uz"))

        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val targetDayBegin = targetCal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis

            val targetDayEnd = targetCal.apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 55)
                set(Calendar.SECOND, 55)
            }.timeInMillis

            val daysFocusMins = history.filter {
                it.date in targetDayBegin..targetDayEnd
            }.sumOf { it.actualFocusTimeSeconds } / 60f

            val dayLabel = sdf.format(targetCal.time)
            dataList.add(dayLabel to daysFocusMins)
        }
        dataList
    }

    val maxVal = remember(chartData) {
        val max = chartData.maxOfOrNull { it.second } ?: 10f
        if (max < 10f) 30f else max * 1.15f
    }

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 8.dp)
        ) {
            val spaceWidth = size.width / (chartData.size * 2 + 1)
            val barWidth = spaceWidth

            chartData.forEachIndexed { index, pair ->
                val x = spaceWidth + index * (barWidth + spaceWidth)
                val focusMins = pair.second
                val barHeight = (focusMins / maxVal) * size.height

                // Draw background capsule track
                drawRoundRect(
                    color = barTrackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2)
                )

                // Draw solid active column
                if (barHeight > 0) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Week labels under the columns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            chartData.forEach { pair ->
                Text(
                    text = pair.first,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun HistoryItemRow(item: SessionHistory, onDelete: () -> Unit) {
    val dateText = remember(item.date) {
        val sdf = SimpleDateFormat("dd-MMM, HH:mm", Locale("uz"))
        sdf.format(Date(item.date))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circle status icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.completed) Color(0xFF4CAF50).copy(alpha = 0.15f)
                            else Color(0xFFF44336).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.completed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (item.completed) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.lessonName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateText • Reja: ${item.durationMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatFocusTime(item.actualFocusTimeSeconds),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "O'chirish",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// --- TAB 3: SETTINGS TAB ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(viewModel: FocusViewModel) {
    val context = LocalContext.current
    
    val themeMode by viewModel.themeMode.collectAsState()
    val amoledMode by viewModel.amoledMode.collectAsState()
    val widgetTransparency by viewModel.widgetTransparency.collectAsState()
    val widgetSize by viewModel.widgetSize.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val dailyGoalMinutes by viewModel.dailyGoalMinutes.collectAsState()
    val rebootAutoStart by viewModel.rebootAutoStart.collectAsState()

    // Permission statuses checked in real-time
    var isAccessibilityOn by remember { mutableStateOf(FocusAccessibilityService.isServiceRunning) }
    var isOverlayOn by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isNotificationOn by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Launchers for backup/restore files
    val exportCSVLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            val success = viewModel.exportHistoryToCSV(it)
            android.widget.Toast.makeText(
                context, 
                if (success) "Eksport muvaffaqiyatli yakunlandi!" else "Eksportda xatolik yuz berdi.", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importCSVLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val success = viewModel.importHistoryFromCSV(it)
            android.widget.Toast.makeText(
                context, 
                if (success) "Ma'lumotlar qayta tiklandi!" else "Fayl formati noto'g'ri.", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Request notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationOn = granted
    }

    // Refresh permission statuses on launch or when screen loads
    LaunchedEffect(Unit) {
        isAccessibilityOn = FocusAccessibilityService.isServiceRunning
        isOverlayOn = Settings.canDrawOverlays(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Sozlamalar",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Section 1: UI Styles
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tashqi ko'rinish",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Theme Select Dropdown / Row
                    val options = listOf("Tizim", "Tungi", "Kunduzgi")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { opt ->
                            FilterChip(
                                selected = themeMode == opt,
                                onClick = { viewModel.saveThemeMode(opt) },
                                label = { Text(opt) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Divider()

                    // AMOLED switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "AMOLED To'liq qora rejim", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "Ekran quvvatini tejash va quyuq qora rang", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = amoledMode, onCheckedChange = { viewModel.saveAmoledMode(it) })
                    }
                }
            }
        }

        // Section 2: Floating widget transparency & size
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Suzib yuruvchi vidjet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Transparency slider
                    Column {
                        Text(
                            text = "Shaffoflik: ${widgetTransparency.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = widgetTransparency,
                            onValueChange = { viewModel.saveWidgetTransparency(it) },
                            valueRange = 20f..100f
                        )
                    }

                    Divider()

                    // Size select
                    val sizeOpts = listOf("Kichik", "O'rtacha", "Katta")
                    Column {
                        Text(
                            text = "Vidjet o'lchami",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sizeOpts.forEach { opt ->
                                FilterChip(
                                    selected = widgetSize == opt,
                                    onClick = { viewModel.saveWidgetSize(opt) },
                                    label = { Text(opt) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Goals & Autostart
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Dars Maqsadlari",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Daily goal Picker / Slider
                    Column {
                        Text(
                            text = "Kunlik maqsad: ${dailyGoalMinutes / 60} soat (${dailyGoalMinutes % 60} daqiqa)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = dailyGoalMinutes.toFloat(),
                            onValueChange = { viewModel.saveDailyGoalMinutes(it.toInt()) },
                            valueRange = 30f..480f,
                            steps = 14 // Increments of 30 minutes
                        )
                    }

                    Divider()

                    // Auto start after reboot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Rebootdan so'ng avto-start", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "Telefon o'chib yonsa seansni davom ettirish", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rebootAutoStart, onCheckedChange = { viewModel.saveRebootAutoStart(it) })
                    }
                }
            }
        }

        // Section 4: Safety permissions status list
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Ruxsatnomalar Holati",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 1. Accessibility Service status
                    PermissionStatusRow(
                        name = "Bloklash xizmati (Accessibility)",
                        granted = isAccessibilityOn,
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )

                    // 2. Overlay status
                    PermissionStatusRow(
                        name = "Ekran ustidan chizish (Overlay)",
                        granted = isOverlayOn,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )

                    // 3. Notification status
                    PermissionStatusRow(
                        name = "Bildirishnomalar (Notification)",
                        granted = isNotificationOn,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                // Direct to App Notification settings
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )

                    // 4. Battery Optimizer
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isBatteryOptimized = pm.isIgnoringBatteryOptimizations(context.packageName)
                    PermissionStatusRow(
                        name = "Batareya optimallashtirish cheklovi",
                        granted = isBatteryOptimized,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // Section 5: Backup & Restore CSV
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Zaxira va Fayllar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportCSVLauncher.launch("focus_shield_tarix.csv")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Eksport (CSV)", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                importCSVLauncher.launch(arrayOf("text/comma-separated-values", "text/csv"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import (CSV)", fontSize = 12.sp)
                        }
                    }

                    Divider()

                    // Clear button
                    var showClearConfirm by remember { mutableStateOf(false) }
                    if (showClearConfirm) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.clearAllHistory()
                                    showClearConfirm = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text("O'chirishni tasdiqlash", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showClearConfirm = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Yo'q", fontSize = 12.sp)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Barcha statistikalarni o'chirish")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(
    name: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                text = if (granted) "Ruxsat berilgan" else "Ruxsat berilmagan (Sozlash uchun bosing)",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )
        }
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.AddCircle,
            contentDescription = null,
            tint = if (granted) Color(0xFF4CAF50) else Color(0xFFFF9800),
            modifier = Modifier.size(24.dp)
        )
    }
}

// --- BLOCKED INTERFERENCE OVERLAY PAGE ---

@Composable
fun BlockedLockScreen(state: FocusSessionState, message: String, onBack: () -> Unit) {
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val h = state.remainingSeconds / 3600
    val m = (state.remainingSeconds % 3600) / 60
    val s = state.remainingSeconds % 60
    val formattedTime = if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Giant pulsing shield icon
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f * pulseScale)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Taqiqlangan",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(76.dp)
                )
            }

            // Big Lock Warning Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Siz hozir darsdasiz!",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Mashg'ulot: ${state.lessonName}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Giant countdown countdown timer
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "QOLGAN DIQQAT VAQTI",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Motivation subtext
            Text(
                text = "Diqqatingizni bo'lmang va o'qishda davom eting. Taymer tugagach blok avtomatik yechiladi.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Done Button / Return button
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dars ekraniga qaytish", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- HELPER METRIC UTILITIES ---

private fun formatFocusTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) {
        "$h soat $m daq"
    } else {
        "$m daqiqa"
    }
}

private fun calculateCurrentStreak(history: List<SessionHistory>): Int {
    val completedDates = history.filter { it.completed }.map {
        val cal = Calendar.getInstance().apply { timeInMillis = it.date }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.distinct().sortedDescending()

    if (completedDates.isEmpty()) return 0

    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayMs = todayCal.timeInMillis
    val yesterdayMs = todayMs - 24 * 60 * 60 * 1000L

    // If first element is neither today nor yesterday, streak is broken (0)
    val firstDate = completedDates[0]
    if (firstDate != todayMs && firstDate != yesterdayMs) return 0

    var streak = 1
    var lastMs = firstDate
    for (i in 1 until completedDates.size) {
        val expectedPrev = lastMs - 24 * 60 * 60 * 1000L
        if (completedDates[i] == expectedPrev) {
            streak++
            lastMs = completedDates[i]
        } else break
    }
    return streak
}

private fun calculateBestStreak(history: List<SessionHistory>): Int {
    val completedDates = history.filter { it.completed }.map {
        val cal = Calendar.getInstance().apply { timeInMillis = it.date }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.distinct().sorted()

    if (completedDates.isEmpty()) return 0

    var maxStreak = 1
    var currentStreak = 1
    for (i in 1 until completedDates.size) {
        val prevMs = completedDates[i - 1]
        val currMs = completedDates[i]
        if (currMs - prevMs == 24 * 60 * 60 * 1000L) {
            currentStreak++
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
            }
        } else {
            currentStreak = 1
        }
    }
    return maxStreak
}
