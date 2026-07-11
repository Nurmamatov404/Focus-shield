package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FocusService : Service(), ViewModelStoreOwner {

    companion object {
        private const val TAG = "FocusService"
        const val CHANNEL_ID = "focus_shield_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
    }

    private lateinit var sessionManager: FocusSessionManager
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isForeground = false
    private var overlayLifecycleOwner: MyServiceLifecycleOwner? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val _viewModelStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override fun onCreate() {
        super.onCreate()
        sessionManager = FocusSessionManager.getInstance(applicationContext)
        createNotificationChannel()

        // Observe session state to dynamically add/remove the floating overlay
        serviceScope.launch {
            sessionManager.sessionState.collectLatest { state ->
                updateNotification(state)
                if (state.isActive && state.showFloatingWidget) {
                    showOverlay(state)
                } else {
                    removeOverlay()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_PAUSE -> sessionManager.pauseSession()
            ACTION_RESUME -> sessionManager.resumeSession()
            ACTION_STOP -> {
                // Since clicking Stop from notification can be quick, we don't want a blocking confirmation dialog,
                // so we end the session (cancelled = true) immediately.
                sessionManager.endSession(isCancelled = true)
                stopSelf()
            }
            ACTION_UPDATE -> {
                // Just triggers notification & overlay updates via flow collection
            }
        }

        // Start as foreground service to prevent getting killed
        startServiceForeground()

        return START_STICKY
    }

    private fun startServiceForeground() {
        val state = sessionManager.sessionState.value
        if (isForeground) {
            updateNotification(state)
            return
        }

        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                isForeground = true
            } catch (e: Exception) {
                Log.e(TAG, "Could not startForeground with specialUse, starting normally", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                    isForeground = true
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed startForeground completely", ex)
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
                isForeground = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed startForeground on old version", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Focus Shield Seansi"
            val descriptionText = "Faol dars seansi taymeri va boshqaruvi"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: FocusSessionState): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, FocusService::class.java).apply { action = ACTION_PAUSE }
        val resumeIntent = Intent(this, FocusService::class.java).apply { action = ACTION_RESUME }
        val stopIntent = Intent(this, FocusService::class.java).apply { action = ACTION_STOP }

        val pPause = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pResume = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pStop = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val formattedTime = formatTime(state.remainingSeconds)
        val titleText = if (state.isPaused) "Dars to'xtatildi: ${state.lessonName}" else "Dars davom etmoqda: ${state.lessonName}"
        val contentText = if (state.isPaused) "Taymer pauzada. Qolgan vaqt: $formattedTime" else "Qolgan vaqt: $formattedTime. Diqqatingizni jamlang!"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Standard default icon or app icon
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (state.isActive) {
            if (state.isPaused) {
                builder.addAction(android.R.drawable.ic_media_play, "Davom etish", pResume)
            } else {
                builder.addAction(android.R.drawable.ic_media_pause, "Pauza", pPause)
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tugatish", pStop)
        }

        return builder.build()
    }

    private fun updateNotification(state: FocusSessionState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (state.isActive) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    // --- Floating Widget Overlay ---

    private fun showOverlay(state: FocusSessionState) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted. Cannot show widget.")
            return
        }

        if (overlayView == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefsX()
                y = prefsY()
            }

            // Create Compose View and attach lifecycle / VM stores
            val context = this
            val customLifecycleOwner = MyServiceLifecycleOwner()
            overlayLifecycleOwner = customLifecycleOwner

            overlayView = ComposeView(context).apply {
                // Set necessary context owners for ComposeView inside service WindowManager
                setViewTreeLifecycleOwner(customLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(customLifecycleOwner)
                setViewTreeViewModelStoreOwner(context)

                setContent {
                    FloatingWidgetContent(
                        sessionManager = sessionManager,
                        onDrag = { dx, dy ->
                            overlayParams?.let { params ->
                                params.x += dx
                                params.y += dy
                                windowManager?.updateViewLayout(this@apply, params)
                                savePrefsPos(params.x, params.y)
                            }
                        },
                        onStartResume = { isPaused ->
                            if (isPaused) sessionManager.resumeSession() else sessionManager.pauseSession()
                        },
                        onEnd = {
                            // Show ending flow
                            sessionManager.endSession(isCancelled = false)
                        },
                        onCloseWidgetOnly = {
                            sessionManager.setShowFloatingWidget(false)
                        }
                    )
                }
            }

            try {
                windowManager?.addView(overlayView, overlayParams)
                Log.d(TAG, "Floating Widget Overlay Added.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add window overlay view", e)
            }
        }
    }

    private fun removeOverlay() {
        overlayLifecycleOwner?.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        overlayLifecycleOwner = null
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.d(TAG, "Floating Widget Overlay Removed.")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
            overlayView = null
        }
    }

    private fun prefsX(): Int = applicationContext.getSharedPreferences("widget_prefs", MODE_PRIVATE).getInt("x", 100)
    private fun prefsY(): Int = applicationContext.getSharedPreferences("widget_prefs", MODE_PRIVATE).getInt("y", 200)
    private fun savePrefsPos(x: Int, y: Int) {
        applicationContext.getSharedPreferences("widget_prefs", MODE_PRIVATE).edit().putInt("x", x).putInt("y", y).apply()
    }

    override fun onDestroy() {
        removeOverlay()
        _viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingWidgetContent(
    sessionManager: FocusSessionManager,
    onDrag: (Int, Int) -> Unit,
    onStartResume: (Boolean) -> Unit,
    onEnd: () -> Unit,
    onCloseWidgetOnly: () -> Unit
) {
    val state by sessionManager.sessionState.collectAsState()
    val context = LocalContext.current
    var showEndConfirm by remember { mutableStateOf(false) }

    // Read transparency from settings
    var transparencyTarget by remember { mutableStateOf(0.85f) }
    val transparency by animateFloatAsState(targetValue = transparencyTarget)
    var widgetSizeScale by remember { mutableStateOf(1.0f) }

    // Read direct settings for widget style
    LaunchedEffect(Unit) {
        val sp = context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
        val trVal = sp.getString("widget_transparency", "85")?.toFloatOrNull() ?: 85f
        transparencyTarget = trVal / 100f
        val sizeVal = sp.getString("widget_size", "O'rtacha") ?: "O'rtacha"
        widgetSizeScale = when (sizeVal) {
            "Kichik" -> 0.8f
            "Katta" -> 1.2f
            else -> 1.0f
        }
    }

    if (showEndConfirm) {
        // Since overlay is not focusable, showing a full dialog here is tricky.
        // We can render a confirmation overlay inside the floating card itself!
        Card(
            modifier = Modifier
                .width((220 * widgetSizeScale).dp)
                .padding(4.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Darsni tugatasizmi?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Diqqatingiz bo'linadi va statistika chala qoladi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showEndConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Yo'q", color = Color.White, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            showEndConfirm = false
                            onEnd()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Ha", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }
    } else {
        // Main glassmorphism widget UI
        Card(
            modifier = Modifier
                .width((220 * widgetSizeScale).dp)
                .padding(4.dp)
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = transparency)
            )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .padding(10.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Header with close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (state.isPaused) Color.Yellow else Color.Green)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = state.lessonName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onCloseWidgetOnly,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Widget yopish",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Countdown timer
                    val formattedTime = remember(state.remainingSeconds) {
                        val h = state.remainingSeconds / 3600
                        val m = (state.remainingSeconds % 3600) / 60
                        val s = state.remainingSeconds % 60
                        if (h > 0) {
                            String.format("%02d:%02d:%02d", h, m, s)
                        } else {
                            String.format("%02d:%02d", m, s)
                        }
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = if (state.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Control Actions inside Widget
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onStartResume(state.isPaused) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (state.isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (state.isPaused) "Boshlash" else "Pauza",
                                modifier = Modifier.size(20.dp),
                                tint = if (state.isPaused) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        IconButton(
                            onClick = { showEndConfirm = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Tugatish",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Unified, State-Safe implementation of Lifecycles for WindowManager Compose Views ---

class MyServiceLifecycleOwner : androidx.lifecycle.LifecycleOwner, androidx.savedstate.SavedStateRegistryOwner {
    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.CREATED
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
    }

    fun handleLifecycleEvent(event: androidx.lifecycle.Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    override val lifecycle: androidx.lifecycle.Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}
