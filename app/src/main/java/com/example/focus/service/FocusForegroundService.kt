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
                LockScreenActivity.launch(this)
                startTicker()
            }
        }
        return START_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val state = FocusRepository.state.value
                if (!state.isLocked) {
                    checkScheduledSessions()
                    removeTopStatusBarBlocker()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                FocusRepository.onTimerTick(this@FocusForegroundService)
                updateNotification()
                updateTopStatusBarBlocker()
                enforceForegroundLockIfNeeded()

                delay(1_000L)
            }
        }
    }

    private fun checkScheduledSessions() {
        val state = FocusRepository.state.value
        if (state.isLocked) return
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dayOfWeek = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // 1=Mon..7=Sun

        val matchingSchedule = state.schedules.firstOrNull { schedule ->
            schedule.enabled &&
                dayOfWeek in schedule.daysOfWeek &&
                schedule.startHour == hour &&
                schedule.startMinute == minute
        }
        if (matchingSchedule != null) {
            val startTotal = matchingSchedule.startHour * 60 + matchingSchedule.startMinute
            val endTotal = matchingSchedule.endHour * 60 + matchingSchedule.endMinute
            val duration = if (endTotal > startTotal) endTotal - startTotal else (24 * 60 - startTotal + endTotal)
            FocusRepository.startFocusSession(
                context = this,
                durationMinutes = duration.coerceAtLeast(5),
                title = matchingSchedule.title,
                mode = SessionMode.FOCUS
            )
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus Faol Sessiya",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Focus va Study rejimida qolgan vaqtni ko'rsatadi"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = FocusRepository.state.value
        val mins = state.remainingSeconds / 60
        val secs = state.remainingSeconds % 60
        val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)

        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = when (state.sessionMode) {
            SessionMode.FOCUS -> "Qolgan vaqt: $timeFormatted • Faqat Telefon, Fonor va Xabarlar"
            SessionMode.STUDY_TIMER -> "O'qish vaqti: $timeFormatted • Bildirishnomalar o'chirilgan"
            SessionMode.STUDY_POMODORO -> "Pomodoro ${state.currentRound}/${state.totalRounds} • Qolgan vaqt: $timeFormatted"
        }

        val stopIntent = Intent(this, FocusForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(state.sessionTitle)
            .setContentText(subtitle)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛠️ Dev Test: To'xtatish", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        removeTopStatusBarBlocker()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "focus_active_session_channel"
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
