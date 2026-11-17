package com.example.focus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.focus.data.FocusRepository
import com.example.focus.data.PomodoroPhase
import com.example.focus.data.SessionMode
import com.example.focus.ui.lock.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class FocusForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var topStatusBarBlockerView: View? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        FocusRepository.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeTopStatusBarBlocker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                FocusRepository.stopSession(this, completed = false)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                updateTopStatusBarBlocker()
                if (FocusRepository.state.value.isLocked) {
                    LockScreenActivity.launch(this)
                }
                startTicker()
            }
        }
        return START_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                FocusRepository.checkAndApplySchedules(this@FocusForegroundService)
                val state = FocusRepository.state.value

                if (state.isLocked) {
                    FocusRepository.onTimerTick(this@FocusForegroundService)
                    updateTopStatusBarBlocker()
                    enforceForegroundLockIfNeeded()
                } else {
                    removeTopStatusBarBlocker()
                    val hasActiveSchedules = state.schedules.any { it.enabled && it.daysOfWeek.isNotEmpty() }
                    if (!hasActiveSchedules) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                }

                updateNotification()
                delay(1_000L)
            }
        }
    }

    private fun enforceForegroundLockIfNeeded() {
        val state = FocusRepository.state.value
        if (!state.isLocked) return

        // If Pomodoro is in break phase and user disabled lock during break, allow rest
        if (state.sessionMode == SessionMode.STUDY_POMODORO &&
            state.pomodoroPhase != PomodoroPhase.WORK &&
            !state.lockDuringPomodoroBreak
        ) {
            return
        }

        // If UsageStats permission is granted, check the current top package
        if (FocusRepository.hasUsageStatsPermission(this)) {
            val topPkg = getTopPackageName()
            if (topPkg != null) {
                val allowedPackages = FocusRepository.getAllowedPackages(this)
                if (topPkg !in allowedPackages) {
                    FocusRepository.grantTemporaryAllowedPackage(null)
                    LockScreenActivity.launch(this)
                }
            }
        }
    }

    private fun getTopPackageName(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val end = System.currentTimeMillis()
            val begin = end - 10_000L
            val events = usm.queryEvents(begin, end)
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastPkg = event.packageName
                }
            }
            lastPkg
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Adds an invisible top-edge overlay strip using TYPE_APPLICATION_OVERLAY
     * to intercept downward swipes on the status bar / Control Panel during Focus lock.
     */
    private fun updateTopStatusBarBlocker() {
        val state = FocusRepository.state.value
        if (!state.isLocked || !state.blockControlPanel || !Settings.canDrawOverlays(this)) {
            removeTopStatusBarBlocker()
            return
        }
        if (topStatusBarBlockerView != null) return

        try {
            val density = resources.displayMetrics.density
            val blockerHeightPx = (42 * density).toInt()

            val blocker = object : View(this) {
                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    // Consume top-edge swipe attempts so Control Panel / Notification Shade cannot be pulled down
                    return true
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                blockerHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            windowManager?.addView(blocker, params)
            topStatusBarBlockerView = blocker
        } catch (_: Exception) {
        }
    }

    private fun removeTopStatusBarBlocker() {
        val view = topStatusBarBlockerView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        } finally {
            topStatusBarBlockerView = null
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // High priority channel for active lock session & full-screen alarm
        val lockChannel = NotificationChannel(
            CHANNEL_ID_LOCK,
            "FocusLock Faol Qulf",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "FocusLock faol bo'lganda ekran qulfi va qolgan vaqtni ko'rsatadi"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        nm.createNotificationChannel(lockChannel)

        // Low priority silent channel for standby guardian mode
        val standbyChannel = NotificationChannel(
            CHANNEL_ID_STANDBY,
            "FocusLock Fon Himoyasi",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keyingi rejalashtirilgan intervalni kutish holati"
            setShowBadge(false)
        }
        nm.createNotificationChannel(standbyChannel)
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = FocusRepository.state.value
        val isLocked = state.isLocked
        val channelId = if (isLocked) CHANNEL_ID_LOCK else CHANNEL_ID_STANDBY

        val lockIntent = Intent(this, if (isLocked) LockScreenActivity::class.java else com.example.focus.MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val subtitle: String
        if (isLocked) {
            val mins = state.remainingSeconds / 60
            val secs = state.remainingSeconds % 60
            val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
            title = state.sessionTitle
            subtitle = when (state.sessionMode) {
                SessionMode.FOCUS -> "Qolgan vaqt: $timeFormatted • Faqat telefon va SMS"
                SessionMode.STUDY_TIMER -> "O'qish vaqti: $timeFormatted • Bildirishnomalar o'chirilgan"
                SessionMode.STUDY_POMODORO -> "Pomodoro ${state.currentRound}/${state.totalRounds} • Qolgan vaqt: $timeFormatted"
            }
        } else {
            title = "FocusLock himoyada"
            val next = FocusRepository.getNextScheduleTimeText()
            subtitle = if (next.isNotBlank()) "Keyingi reja: $next" else "Rejalashtirilgan intervallar kutilmoqda"
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (isLocked) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        }

        return builder.build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val state = FocusRepository.state.value
        val hasActiveSchedules = state.schedules.any { it.enabled && it.daysOfWeek.isNotEmpty() }
        if (state.isLocked || hasActiveSchedules) {
            val restartIntent = Intent(applicationContext, FocusForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
            if (state.isLocked) {
                LockScreenActivity.launch(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        removeTopStatusBarBlocker()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID_LOCK = "focus_lock_session_channel_v2"
        private const val CHANNEL_ID_STANDBY = "focus_standby_channel_v1"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.example.focus.action.START_SESSION"
        private const val ACTION_STOP = "com.example.focus.action.STOP_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, FocusForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
