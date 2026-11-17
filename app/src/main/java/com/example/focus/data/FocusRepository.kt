package com.example.focus.data

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.text.TextUtils
import com.example.focus.receiver.FocusScheduleReceiver
import com.example.focus.service.FocusAccessibilityService
import com.example.focus.service.FocusForegroundService
import com.example.focus.service.FocusNotificationListenerService
import com.example.focus.ui.lock.LockScreenActivity
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class SessionMode {
    FOCUS,
    STUDY_TIMER,
    STUDY_POMODORO
}

enum class PomodoroPhase {
    WORK,
    SHORT_BREAK,
    LONG_BREAK
}

data class FocusSchedule(
    val id: String,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5) // 1=Mon .. 7=Sun
)

data class FocusState(
    val isLocked: Boolean = false,
    val sessionMode: SessionMode = SessionMode.FOCUS,
    val sessionTitle: String = "Focus rejimi",
    val totalDurationSeconds: Long = 0L,
    val remainingSeconds: Long = 0L,
    val endTimeEpochMillis: Long = 0L,
    // Pomodoro state
    val pomodoroPhase: PomodoroPhase = PomodoroPhase.WORK,
    val currentRound: Int = 1,
    val totalRounds: Int = 4,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    // Torch / Flashlight state
    val isTorchOn: Boolean = false,
    val hasFlashUnit: Boolean = true,
    // Temporary allowed app access window (when user opens Phone or Messages from LockScreen)
    val activeAllowedPackage: String? = null,
    val allowedPackageEnteredAtMillis: Long = 0L,
    // Schedules & stats
    val schedules: List<FocusSchedule> = emptyList(),
    val completedSessionsCount: Int = 0,
    val totalFocusedMinutes: Int = 0,
    // Settings
    val strictMode: Boolean = true,
    val autoDndAndMute: Boolean = true,
    val blockControlPanel: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val useScreenPinning: Boolean = false,
    val lockDuringPomodoroBreak: Boolean = true,
    val darkThemeMode: Int = 0, // 0 = System, 1 = Light, 2 = Dark
    val dynamicColors: Boolean = true,
    val githubOwner: String = "joxacybers",
    val githubRepo: String = "focus"
)

object FocusRepository {
    private const val PREFS_NAME = "focus_prefs"
    private lateinit var prefs: SharedPreferences
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var previousInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    private var previousRingerMode: Int = AudioManager.RINGER_MODE_NORMAL

    // Monotonic elapsed realtime anti-cheat tracking (cannot be bypassed by changing system date/time)
    private var sessionStartElapsedRealtime: Long = 0L
    private var sessionDurationMillis: Long = 0L

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _state.update { it.copy(isTorchOn = enabled) }
            }
        }
    }

    private var applicationContext: Context? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        if (!::prefs.isInitialized) {
            prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            setupCameraTorch(appContext)
            loadFromPrefs()
        }
        rescheduleAlarms(appContext)
        checkAndApplySchedules(appContext)
        FocusForegroundService.start(appContext)
    }

    private fun setupCameraTorch(context: Context) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager = cm
            val id = cm?.cameraIdList?.firstOrNull { camId ->
                val characteristics = cm.getCameraCharacteristics(camId)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            torchCameraId = id
            _state.update { it.copy(hasFlashUnit = id != null) }
            cm?.registerTorchCallback(torchCallback, null)
        } catch (_: Exception) {
            _state.update { it.copy(hasFlashUnit = false) }
        }
    }

    fun toggleTorch(): Boolean {
        val cm = cameraManager ?: return false
        val id = torchCameraId ?: return false
        val nextState = !_state.value.isTorchOn
        return try {
            cm.setTorchMode(id, nextState)
            _state.update { it.copy(isTorchOn = nextState) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun turnOffTorchIfOn() {
        if (_state.value.isTorchOn) {
            try {
                torchCameraId?.let { cameraManager?.setTorchMode(it, false) }
                _state.update { it.copy(isTorchOn = false) }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadFromPrefs() {
        val strictMode = prefs.getBoolean("strictMode", true)
        val autoDndAndMute = prefs.getBoolean("autoDndAndMute", true)
        val blockControlPanel = prefs.getBoolean("blockControlPanel", true)
        val keepScreenAwake = prefs.getBoolean("keepScreenAwake", true)
        val useScreenPinning = prefs.getBoolean("useScreenPinning", false)
        val lockDuringBreak = prefs.getBoolean("lockDuringPomodoroBreak", true)
        val darkThemeMode = prefs.getInt("darkThemeMode", 0)
        val dynamicColors = prefs.getBoolean("dynamicColors", true)
        val githubOwner = prefs.getString("githubOwner", "joxacybers") ?: "joxacybers"
        val githubRepo = prefs.getString("githubRepo", "focus") ?: "focus"
        val completedCount = prefs.getInt("completedSessionsCount", 0)
        val totalMinutes = prefs.getInt("totalFocusedMinutes", 0)
        val schedules = parseSchedules(prefs.getString("schedules_json", null))

        // Check if there was an active session that hasn't expired yet
        val savedEndTime = prefs.getLong("active_end_time", 0L)
        val now = System.currentTimeMillis()
        val stillActive = savedEndTime > now
        sessionStartElapsedRealtime = prefs.getLong("session_start_elapsed", 0L)
        sessionDurationMillis = prefs.getLong("session_duration_millis", 0L)

        _state.update { current ->
            current.copy(
                strictMode = strictMode,
                autoDndAndMute = autoDndAndMute,
                blockControlPanel = blockControlPanel,
                keepScreenAwake = keepScreenAwake,
                useScreenPinning = useScreenPinning,
                lockDuringPomodoroBreak = lockDuringBreak,
                darkThemeMode = darkThemeMode,
                dynamicColors = dynamicColors,
                githubOwner = githubOwner,
                githubRepo = githubRepo,
                completedSessionsCount = completedCount,
                totalFocusedMinutes = totalMinutes,
                schedules = schedules,
                isLocked = stillActive,
                endTimeEpochMillis = if (stillActive) savedEndTime else 0L,
                remainingSeconds = if (stillActive) ((savedEndTime - now) / 1000L).coerceAtLeast(0L) else 0L,
                totalDurationSeconds = if (stillActive) prefs.getLong("active_total_seconds", 60L) else 0L,
                sessionTitle = if (stillActive) (prefs.getString("active_title", "Focus rejimi") ?: "Focus rejimi") else "Focus rejimi",
                sessionMode = if (stillActive) {
                    runCatching {
                        SessionMode.valueOf(prefs.getString("active_mode", SessionMode.FOCUS.name) ?: SessionMode.FOCUS.name)
                    }.getOrDefault(SessionMode.FOCUS)
                } else SessionMode.FOCUS
            )
        }
    }

    private fun parseSchedules(json: String?): List<FocusSchedule> {
        if (json.isNullOrBlank()) {
            return listOf(
                FocusSchedule(
                    id = "default_morning",
                    title = "Ertalabki chuqur diqqat",
                    startHour = 9,
                    startMinute = 0,
                    endHour = 11,
                    endMinute = 0,
                    enabled = false,
                    daysOfWeek = setOf(1, 2, 3, 4, 5)
                ),
                FocusSchedule(
                    id = "default_study",
                    title = "Kechki o'qish vaqti",
                    startHour = 19,
                    startMinute = 0,
                    endHour = 21,
                    endMinute = 0,
                    enabled = false,
                    daysOfWeek = setOf(1, 2, 3, 4, 5, 6)
                )
            )
        }
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<FocusSchedule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val daysArray = obj.optJSONArray("days")
                val days = mutableSetOf<Int>()
                if (daysArray != null) {
                    for (j in 0 until daysArray.length()) {
                        days.add(daysArray.getInt(j))
                    }
                } else {
                    days.addAll(listOf(1, 2, 3, 4, 5))
                }
                list.add(
                    FocusSchedule(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        startHour = obj.getInt("startHour"),
                        startMinute = obj.getInt("startMinute"),
                        endHour = obj.getInt("endHour"),
                        endMinute = obj.getInt("endMinute"),
                        enabled = obj.getBoolean("enabled"),
                        daysOfWeek = days
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSchedules(schedules: List<FocusSchedule>) {
        val arr = JSONArray()
        schedules.forEach { schedule ->
            val obj = JSONObject().apply {
                put("id", schedule.id)
                put("title", schedule.title)
                put("startHour", schedule.startHour)
                put("startMinute", schedule.startMinute)
                put("endHour", schedule.endHour)
                put("endMinute", schedule.endMinute)
                put("enabled", schedule.enabled)
                val days = JSONArray()
                schedule.daysOfWeek.forEach { days.put(it) }
                put("days", days)
            }
            arr.put(obj)
        }
        prefs.edit().putString("schedules_json", arr.toString()).apply()
    }

    data class ActiveScheduleMatch(
        val schedule: FocusSchedule,
        val remainingMinutes: Int
    )

    // Tracks if user purposefully unlocked/dismissed an active schedule occurrence so it doesn't immediately re-lock
    private var dismissedScheduleId: String? = null
    private var dismissedScheduleDay: Int = -1

    fun findActiveSchedule(
        schedules: List<FocusSchedule>,
        nowCal: Calendar = Calendar.getInstance()
    ): ActiveScheduleMatch? {
        val dayOfWeek = ((nowCal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val currentMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        for (schedule in schedules) {
            if (!schedule.enabled) continue
            val startMinutes = schedule.startHour * 60 + schedule.startMinute
            val endMinutes = schedule.endHour * 60 + schedule.endMinute

            var isMatch = false
            var remaining = 0

            if (endMinutes > startMinutes) {
                // Same-day interval, e.g. 09:00 to 12:00
                if (dayOfWeek in schedule.daysOfWeek && currentMinutes in startMinutes until endMinutes) {
                    isMatch = true
                    remaining = endMinutes - currentMinutes
                }
            } else if (endMinutes < startMinutes) {
                // Overnight interval, e.g. 22:00 to 06:00
                if (dayOfWeek in schedule.daysOfWeek && currentMinutes >= startMinutes) {
                    isMatch = true
                    remaining = (24 * 60 - currentMinutes) + endMinutes
                } else {
                    val prevDayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1
                    if (prevDayOfWeek in schedule.daysOfWeek && currentMinutes < endMinutes) {
                        isMatch = true
                        remaining = endMinutes - currentMinutes
                    }
                }
            }

            if (isMatch) {
                // If user dismissed this exact schedule occurrence earlier, skip until it ends
                if (dismissedScheduleId == schedule.id && dismissedScheduleDay == dayOfWeek) {
                    continue
                }
                return ActiveScheduleMatch(schedule, remaining.coerceAtLeast(1))
            }
        }
        return null
    }

    fun checkAndApplySchedules(context: Context) {
        val current = _state.value
        val match = findActiveSchedule(current.schedules)
        if (match != null) {
            if (!current.isLocked) {
                startFocusSession(
                    context = context,
                    durationMinutes = match.remainingMinutes,
                    title = match.schedule.title,
                    mode = SessionMode.FOCUS
                )
            }
            LockScreenActivity.launch(context)
        }
    }

    fun rescheduleAlarms(context: Context) {
        val schedules = _state.value.schedules.filter { it.enabled && it.daysOfWeek.isNotEmpty() }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusScheduleReceiver::class.java).apply {
            action = FocusScheduleReceiver.ACTION_CHECK_SCHEDULES
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            FocusScheduleReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (schedules.isEmpty()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val now = System.currentTimeMillis()
        val triggerCandidates = mutableListOf<Long>()

        for (schedule in schedules) {
            for (offsetDays in 0..7) {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, offsetDays)
                }
                val isoDay = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                if (isoDay in schedule.daysOfWeek) {
                    // Schedule start time
                    val startCal = Calendar.getInstance().apply {
                        timeInMillis = cal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, schedule.startHour)
                        set(Calendar.MINUTE, schedule.startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (startCal.timeInMillis > now) {
                        triggerCandidates.add(startCal.timeInMillis)
                    }

                    // Schedule end time
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = cal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, schedule.endHour)
                        set(Calendar.MINUTE, schedule.endMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (schedule.endHour < schedule.startHour ||
                        (schedule.endHour == schedule.startHour && schedule.endMinute < schedule.startMinute)
                    ) {
                        endCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    if (endCal.timeInMillis > now) {
                        triggerCandidates.add(endCal.timeInMillis)
                    }
                }
            }
        }

        // If an interval schedule is active right now, also register a watchdog trigger in 60s
        // so that if the user swipes away the app from recents or task killer runs, it re-locks promptly!
        val activeMatch = findActiveSchedule(schedules)
        if (activeMatch != null) {
            triggerCandidates.add(now + 60_000L)
        }

        val nextTrigger = triggerCandidates.minOrNull()
        if (nextTrigger != null) {
            setPreciseAlarm(context, alarmManager, nextTrigger, pendingIntent)
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun setPreciseAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerTimeMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val showIntent = Intent(context, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. Primary: setAlarmClock (bypasses Doze mode, exact, highest system priority, doesn't require SCHEDULE_EXACT_ALARM)
        try {
            val clockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, showPendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            return
        } catch (_: Exception) {
        }

        // 2. Secondary: setExactAndAllowWhileIdle
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent
                    )
                    return
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
                return
            }
        } catch (_: Exception) {
        }

        // 3. Fallback: setAndAllowWhileIdle
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
        } catch (_: Exception) {
        }
    }

    fun addSchedule(schedule: FocusSchedule, context: Context? = null) {
        val updated = _state.value.schedules + schedule
        _state.update { it.copy(schedules = updated) }
        saveSchedules(updated)
        val ctx = context ?: applicationContext
        if (ctx != null) {
            FocusForegroundService.start(ctx)
            rescheduleAlarms(ctx)
            checkAndApplySchedules(ctx)
        }
    }

    fun toggleSchedule(id: String, enabled: Boolean, context: Context? = null) {
        val updated = _state.value.schedules.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        _state.update { it.copy(schedules = updated) }
        saveSchedules(updated)
        val ctx = context ?: applicationContext
        if (ctx != null) {
            FocusForegroundService.start(ctx)
            rescheduleAlarms(ctx)
            checkAndApplySchedules(ctx)
        }
    }

    fun deleteSchedule(id: String, context: Context? = null) {
        val updated = _state.value.schedules.filterNot { it.id == id }
        _state.update { it.copy(schedules = updated) }
        saveSchedules(updated)
        val ctx = context ?: applicationContext
        if (ctx != null) {
            FocusForegroundService.start(ctx)
            rescheduleAlarms(ctx)
        }
    }

    fun startFocusSession(
        context: Context,
        durationMinutes: Int,
        title: String = "Focus rejimi",
        mode: SessionMode = SessionMode.FOCUS
    ) {
        val totalSeconds = (durationMinutes.coerceAtLeast(1) * 60L)
        val now = System.currentTimeMillis()
        val endTime = now + totalSeconds * 1000L
        sessionStartElapsedRealtime = SystemClock.elapsedRealtime()
        sessionDurationMillis = totalSeconds * 1000L

        prefs.edit()
            .putLong("active_end_time", endTime)
            .putLong("active_total_seconds", totalSeconds)
            .putLong("session_start_elapsed", sessionStartElapsedRealtime)
            .putLong("session_duration_millis", sessionDurationMillis)
            .putString("active_title", title)
            .putString("active_mode", mode.name)
            .apply()

        _state.update {
            it.copy(
                isLocked = true,
                sessionMode = mode,
                sessionTitle = title,
                totalDurationSeconds = totalSeconds,
                remainingSeconds = totalSeconds,
                endTimeEpochMillis = endTime,
                pomodoroPhase = PomodoroPhase.WORK,
                currentRound = 1,
                totalRounds = 1,
                activeAllowedPackage = null
            )
        }

        applySilenceAndDnd(context, enable = true)
        FocusForegroundService.start(context)
    }

    fun startPomodoroSession(
        context: Context,
        rounds: Int,
        workMinutes: Int,
        breakMinutes: Int,
        title: String = "Pomodoro O'qish"
    ) {
        val safeRounds = rounds.coerceIn(1, 12)
        val safeWork = workMinutes.coerceIn(1, 180)
        val safeBreak = breakMinutes.coerceIn(1, 60)
        val totalSeconds = safeWork * 60L
        val endTime = System.currentTimeMillis() + totalSeconds * 1000L

        prefs.edit()
            .putLong("active_end_time", endTime)
            .putLong("active_total_seconds", totalSeconds)
            .putString("active_title", title)
            .putString("active_mode", SessionMode.STUDY_POMODORO.name)
            .apply()

        _state.update {
            it.copy(
                isLocked = true,
                sessionMode = SessionMode.STUDY_POMODORO,
                sessionTitle = title,
                totalDurationSeconds = totalSeconds,
                remainingSeconds = totalSeconds,
                endTimeEpochMillis = endTime,
                pomodoroPhase = PomodoroPhase.WORK,
                currentRound = 1,
                totalRounds = safeRounds,
                workMinutes = safeWork,
                breakMinutes = safeBreak,
                activeAllowedPackage = null
            )
        }

        applySilenceAndDnd(context, enable = true)
        FocusForegroundService.start(context)
    }

    /**
     * Called every second by FocusForegroundService.
     * Handles Pomodoro transitions and session completion.
     */
    fun onTimerTick(context: Context) {
        val current = _state.value
        if (!current.isLocked) return

        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedSinceStart = (nowElapsed - sessionStartElapsedRealtime).coerceAtLeast(0L)
        val remainingByElapsed = ((sessionDurationMillis - elapsedSinceStart) / 1000L).coerceAtLeast(0L)

        // Monotonic check: if elapsedRealtime tracking is valid, use it to prevent system time change cheat
        val remaining = if (sessionDurationMillis > 0L && sessionStartElapsedRealtime > 0L) {
            remainingByElapsed
        } else {
            val now = System.currentTimeMillis()
            ((current.endTimeEpochMillis - now) / 1000L).coerceAtLeast(0L)
        }

        // Expire temporary allowed package window after 3 minutes of inactivity or check in service
        if (remaining > 0L) {
            _state.update { it.copy(remainingSeconds = remaining) }
            return
        }

        // Current phase finished!
        if (current.sessionMode == SessionMode.STUDY_POMODORO) {
            if (current.pomodoroPhase == PomodoroPhase.WORK) {
                // Record completed work minutes
                recordCompletedMinutes(current.workMinutes, incrementSession = current.currentRound == current.totalRounds)

                if (current.currentRound < current.totalRounds) {
                    // Transition to Break phase
                    val isLongBreak = current.currentRound % 4 == 0
                    val nextBreakMins = if (isLongBreak) (current.breakMinutes * 2).coerceAtMost(30) else current.breakMinutes
                    val nextSeconds = nextBreakMins * 60L
                    val nextEnd = System.currentTimeMillis() + nextSeconds * 1000L

                    prefs.edit()
                        .putLong("active_end_time", nextEnd)
                        .putLong("active_total_seconds", nextSeconds)
                        .apply()

                    _state.update {
                        it.copy(
                            pomodoroPhase = if (isLongBreak) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK,
                            totalDurationSeconds = nextSeconds,
                            remainingSeconds = nextSeconds,
                            endTimeEpochMillis = nextEnd
                        )
                    }
                    return
                } else {
                    // All Pomodoro rounds completed!
                    stopSession(context, completed = false) // already recorded above
                    return
                }
            } else {
                // Break finished -> start next Work round
                val nextRound = current.currentRound + 1
                val nextSeconds = current.workMinutes * 60L
                val nextEnd = System.currentTimeMillis() + nextSeconds * 1000L

                prefs.edit()
                    .putLong("active_end_time", nextEnd)
                    .putLong("active_total_seconds", nextSeconds)
                    .apply()

                _state.update {
                    it.copy(
                        pomodoroPhase = PomodoroPhase.WORK,
                        currentRound = nextRound,
                        totalDurationSeconds = nextSeconds,
                        remainingSeconds = nextSeconds,
                        endTimeEpochMillis = nextEnd
                    )
                }
                return
            }
        } else {
            // Standard Focus or Study timer finished
            val mins = (current.totalDurationSeconds / 60L).toInt().coerceAtLeast(1)
            recordCompletedMinutes(mins, incrementSession = true)
            stopSession(context, completed = false)
        }
    }

    private fun recordCompletedMinutes(minutes: Int, incrementSession: Boolean) {
        val newCount = _state.value.completedSessionsCount + if (incrementSession) 1 else 0
        val newMinutes = _state.value.totalFocusedMinutes + minutes
        prefs.edit()
            .putInt("completedSessionsCount", newCount)
            .putInt("totalFocusedMinutes", newMinutes)
            .apply()
        _state.update {
            it.copy(
                completedSessionsCount = newCount,
                totalFocusedMinutes = newMinutes
            )
        }
    }

    fun stopSession(context: Context, completed: Boolean = false) {
        if (completed) {
            val elapsedMins = ((_state.value.totalDurationSeconds - _state.value.remainingSeconds) / 60L)
                .toInt()
                .coerceAtLeast(1)
            recordCompletedMinutes(elapsedMins, incrementSession = true)
        }

        prefs.edit()
            .remove("active_end_time")
            .remove("active_total_seconds")
            .remove("active_title")
            .remove("active_mode")
            .apply()

        turnOffTorchIfOn()
        applySilenceAndDnd(context, enable = false)

        _state.update {
            it.copy(
                isLocked = false,
                remainingSeconds = 0L,
                endTimeEpochMillis = 0L,
                activeAllowedPackage = null
            )
        }

        if (!completed) {
            val cal = Calendar.getInstance()
            val dayOfWeek = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
            val currentSchedule = findActiveSchedule(_state.value.schedules, cal)
            if (currentSchedule != null) {
                dismissedScheduleId = currentSchedule.schedule.id
                dismissedScheduleDay = dayOfWeek
            }
        }

        FocusForegroundService.stop(context)
        rescheduleAlarms(context)
    }

    fun grantTemporaryAllowedPackage(packageName: String?) {
        _state.update {
            it.copy(
                activeAllowedPackage = packageName,
                allowedPackageEnteredAtMillis = if (packageName != null) System.currentTimeMillis() else 0L
            )
        }
    }

    fun updateSettings(
        strictMode: Boolean = _state.value.strictMode,
        autoDndAndMute: Boolean = _state.value.autoDndAndMute,
        blockControlPanel: Boolean = _state.value.blockControlPanel,
        keepScreenAwake: Boolean = _state.value.keepScreenAwake,
        useScreenPinning: Boolean = _state.value.useScreenPinning,
        lockDuringPomodoroBreak: Boolean = _state.value.lockDuringPomodoroBreak,
        darkThemeMode: Int = _state.value.darkThemeMode,
        dynamicColors: Boolean = _state.value.dynamicColors,
        githubOwner: String = _state.value.githubOwner,
        githubRepo: String = _state.value.githubRepo
    ) {
        prefs.edit()
            .putBoolean("strictMode", strictMode)
            .putBoolean("autoDndAndMute", autoDndAndMute)
            .putBoolean("blockControlPanel", blockControlPanel)
            .putBoolean("keepScreenAwake", keepScreenAwake)
            .putBoolean("useScreenPinning", useScreenPinning)
            .putBoolean("lockDuringPomodoroBreak", lockDuringPomodoroBreak)
            .putInt("darkThemeMode", darkThemeMode)
            .putBoolean("dynamicColors", dynamicColors)
            .putString("githubOwner", githubOwner.trim())
            .putString("githubRepo", githubRepo.trim())
            .apply()

        _state.update {
            it.copy(
                strictMode = strictMode,
                autoDndAndMute = autoDndAndMute,
                blockControlPanel = blockControlPanel,
                keepScreenAwake = keepScreenAwake,
                useScreenPinning = useScreenPinning,
                lockDuringPomodoroBreak = lockDuringPomodoroBreak,
                darkThemeMode = darkThemeMode,
                dynamicColors = dynamicColors,
                githubOwner = githubOwner.trim(),
                githubRepo = githubRepo.trim()
            )
        }
    }

    fun applySilenceAndDnd(context: Context, enable: Boolean) {
        if (!_state.value.autoDndAndMute && enable) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        try {
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                if (enable) {
                    previousInterruptionFilter = nm.currentInterruptionFilter
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                } else {
                    nm.setInterruptionFilter(previousInterruptionFilter)
                }
            }
        } catch (_: Exception) {
        }

        try {
            if (am != null) {
                if (enable) {
                    previousRingerMode = am.ringerMode
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                } else {
                    am.ringerMode = previousRingerMode
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Resolves the allowed packages:
     * 1. Focus app itself
     * 2. Default Phone / Dialer & InCallUI packages
     * 3. Default SMS / Messages package
     * 4. System Permission / Package Installer (when updating)
     */
    fun getAllowedPackages(context: Context): Set<String> {
        val allowed = mutableSetOf(
            context.packageName,
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.android.phone"
        )

        getDefaultDialerPackage(context)?.let { allowed.add(it) }
        getDefaultSmsPackage(context)?.let { allowed.add(it) }

        return allowed
    }

    fun getDefaultDialerPackage(context: Context): String? {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val defaultDialer = telecomManager?.defaultDialerPackage
        if (!defaultDialer.isNullOrBlank()) return defaultDialer

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:")
        }
        val resolveInfo = context.packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    fun getDefaultSmsPackage(context: Context): String? {
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
        if (!defaultSms.isNullOrBlank()) return defaultSms

        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:")
        }
        val resolveInfo = context.packageManager.resolveActivity(smsIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    fun launchDialerApp(context: Context): Boolean {
        return try {
            val dialerPkg = getDefaultDialerPackage(context)
            grantTemporaryAllowedPackage(dialerPkg ?: "com.android.dialer")

            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchMessagesApp(context: Context): Boolean {
        return try {
            val smsPkg = getDefaultSmsPackage(context)
            grantTemporaryAllowedPackage(smsPkg ?: "com.google.android.apps.messaging")

            val launchIntent = if (smsPkg != null) {
                context.packageManager.getLaunchIntentForPackage(smsPkg)
            } else null

            val intent = (launchIntent ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
            }).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    // System permission checks
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasDndPermission(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, FocusAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, FocusNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    fun isInLockTaskMode(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
            }
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
            return am.canScheduleExactAlarms()
        }
        return true
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val cn = ComponentName(context, com.example.focus.receiver.FocusDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(cn)
    }

    fun requestDeviceAdmin(context: Context) {
        val cn = ComponentName(context, com.example.focus.receiver.FocusDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "FocusLock qat'iy rejimida bloklash vaqtida ilovani o'chirib yuborishning oldini olish uchun Qurilma Administratori huquqini bering."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getNextScheduleTimeText(): String {
        val schedules = _state.value.schedules.filter { it.enabled && it.daysOfWeek.isNotEmpty() }
        if (schedules.isEmpty()) return ""

        val now = Calendar.getInstance()
        val nowTime = System.currentTimeMillis()

        var earliestCandidate: Long? = null
        var earliestSchedule: FocusSchedule? = null

        for (schedule in schedules) {
            for (offsetDays in 0..7) {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, offsetDays)
                }
                val isoDay = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                if (isoDay in schedule.daysOfWeek) {
                    val startCal = Calendar.getInstance().apply {
                        timeInMillis = cal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, schedule.startHour)
                        set(Calendar.MINUTE, schedule.startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (startCal.timeInMillis > nowTime) {
                        if (earliestCandidate == null || startCal.timeInMillis < earliestCandidate) {
                            earliestCandidate = startCal.timeInMillis
                            earliestSchedule = schedule
                        }
                    }
                }
            }
        }

        val cand = earliestCandidate ?: return ""
        val candCal = Calendar.getInstance().apply { timeInMillis = cand }
        val isToday = candCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val isTomorrow = candCal.get(Calendar.DAY_OF_YEAR) == (now.get(Calendar.DAY_OF_YEAR) + 1)
        val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", candCal.get(Calendar.HOUR_OF_DAY), candCal.get(Calendar.MINUTE))

        return when {
            isToday -> "Bugun $timeStr (${earliestSchedule?.title ?: ""})"
            isTomorrow -> "Ertaga $timeStr (${earliestSchedule?.title ?: ""})"
            else -> {
                val dayNames = listOf("Du", "Se", "Chor", "Pay", "Juma", "Shan", "Yak")
                val isoDay = ((candCal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                "${dayNames.getOrElse(isoDay - 1) { "" }} $timeStr"
            }
        }
    }
}
