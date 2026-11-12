package com.example.attention

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class CustomSchedule(
    val id: String,
    val title: String,
    val startH: Int,
    val startM: Int,
    val endH: Int,
    val endM: Int,
    val enabled: Boolean
)

data class ActiveScheduleInfo(
    val name: String,
    val startTime: Long,
    val endTime: Long
)

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("attention_prefs", Context.MODE_PRIVATE)

    fun isAppBlocked(packageName: String): Boolean {
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        return blockedApps.contains(packageName)
    }

    fun setAppBlocked(packageName: String, blocked: Boolean) {
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (blocked) blockedApps.add(packageName) else blockedApps.remove(packageName)
        prefs.edit().putStringSet("blocked_apps", blockedApps).apply()
    }

    // --- GLOBAL (FOCUS) BLOCK LOGIC ---
    fun isGlobalBlockActive(): Boolean {
        if (prefs.getBoolean("global_block_is_paused", false)) return true
        return System.currentTimeMillis() < prefs.getLong("global_block_end_time", 0L)
    }
    
    fun getGlobalBlockEndTime(): Long {
        if (prefs.getBoolean("global_block_is_paused", false)) {
            return System.currentTimeMillis() + prefs.getLong("global_block_paused_remaining", 0L)
        }
        return prefs.getLong("global_block_end_time", 0L)
    }
    
    fun getGlobalBlockStartTime(): Long = prefs.getLong("global_block_start_time", 0L)
    fun getCurrentTaskName(): String = prefs.getString("global_block_task_name", "Fokus") ?: "Fokus"

    fun startGlobalBlock(durationMillis: Long, taskName: String) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis
        prefs.edit()
            .putLong("global_block_start_time", startTime)
            .putLong("global_block_end_time", endTime)
            .putString("global_block_task_name", taskName)
            .putBoolean("global_block_is_paused", false)
            .apply()
    }

    fun stopGlobalBlock() {
        prefs.edit()
            .putLong("global_block_start_time", 0L)
            .putLong("global_block_end_time", 0L)
            .putString("global_block_task_name", "")
            .putBoolean("global_block_is_paused", false)
            .apply()
    }

    fun pauseGlobalBlock() {
        if (!isGlobalBlockActive() || prefs.getBoolean("global_block_is_paused", false)) return
        val remaining = prefs.getLong("global_block_end_time", 0L) - System.currentTimeMillis()
        if (remaining > 0) {
            prefs.edit()
                .putBoolean("global_block_is_paused", true)
                .putLong("global_block_paused_remaining", remaining)
                .apply()
        }
    }

    fun resumeGlobalBlock() {
        if (prefs.getBoolean("global_block_is_paused", false)) {
            val remaining = prefs.getLong("global_block_paused_remaining", 0L)
            prefs.edit()
                .putBoolean("global_block_is_paused", false)
                .putLong("global_block_end_time", System.currentTimeMillis() + remaining)
                .apply()
        }
    }

    // --- SLEEP MODE LOGIC ---
    fun isSleepModeEnabled(): Boolean = prefs.getBoolean("sleep_mode_enabled", false)
    fun setSleepModeEnabled(enabled: Boolean) = prefs.edit().putBoolean("sleep_mode_enabled", enabled).apply()
    fun setSleepStart(hour: Int, minute: Int) = prefs.edit().putInt("sleep_start_hour", hour).putInt("sleep_start_minute", minute).apply()
    fun setSleepEnd(hour: Int, minute: Int) = prefs.edit().putInt("sleep_end_hour", hour).putInt("sleep_end_minute", minute).apply()
    fun getSleepStartHour(): Int = prefs.getInt("sleep_start_hour", 22)
    fun getSleepStartMinute(): Int = prefs.getInt("sleep_start_minute", 30)
    fun getSleepEndHour(): Int = prefs.getInt("sleep_end_hour", 7)
    fun getSleepEndMinute(): Int = prefs.getInt("sleep_end_minute", 0)

    // --- CUSTOM SCHEDULES LOGIC ---
    fun getCustomSchedules(): List<CustomSchedule> {
        val jsonStr = prefs.getString("custom_schedules", "[]") ?: "[]"
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<CustomSchedule>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(CustomSchedule(
                obj.getString("id"), obj.getString("title"),
                obj.getInt("startH"), obj.getInt("startM"),
                obj.getInt("endH"), obj.getInt("endM"),
                obj.getBoolean("enabled")
            ))
        }
        return list
    }

    fun saveCustomSchedules(list: List<CustomSchedule>) {
        val arr = JSONArray()
        list.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("title", s.title)
            obj.put("startH", s.startH)
            obj.put("startM", s.startM)
            obj.put("endH", s.endH)
            obj.put("endM", s.endM)
            obj.put("enabled", s.enabled)
            arr.put(obj)
        }
        prefs.edit().putString("custom_schedules", arr.toString()).apply()
    }

    // --- ACTIVE SCHEDULE DETECTION ---
    fun getActiveSchedule(): ActiveScheduleInfo? {
        val cal = Calendar.getInstance()
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // 1. Check Sleep Mode
        if (isSleepModeEnabled()) {
            val start = getSleepStartHour() * 60 + getSleepStartMinute()
            val end = getSleepEndHour() * 60 + getSleepEndMinute()
            val isActive = if (start < end) currentMins in start..end else (currentMins >= start || currentMins < end)
            if (isActive) {
                return ActiveScheduleInfo("Uyqu rejimi", calculateTime(getSleepStartHour(), getSleepStartMinute(), getSleepEndHour(), getSleepEndMinute()).first, calculateTime(getSleepStartHour(), getSleepStartMinute(), getSleepEndHour(), getSleepEndMinute()).second)
            }
        }

        // 2. Check Custom Schedules
        val schedules = getCustomSchedules()
        for (s in schedules) {
            if (!s.enabled) continue
            val start = s.startH * 60 + s.startM
            val end = s.endH * 60 + s.endM
            val isActive = if (start < end) currentMins in start..end else (currentMins >= start || currentMins < end)
            if (isActive) {
                val times = calculateTime(s.startH, s.startM, s.endH, s.endM)
                return ActiveScheduleInfo(s.title, times.first, times.second)
            }
        }
        return null
    }

    private fun calculateTime(startH: Int, startM: Int, endH: Int, endM: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = startH * 60 + startM
        val endMins = endH * 60 + endM

        val startCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, startH); set(Calendar.MINUTE, startM); set(Calendar.SECOND, 0) }
        if (startMins > endMins && currentMins < endMins) {
            startCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val endCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, endH); set(Calendar.MINUTE, endM); set(Calendar.SECOND, 0) }
        var endMs = endCal.timeInMillis
        if (endMs < startCal.timeInMillis) {
            endCal.add(Calendar.DAY_OF_YEAR, 1)
            endMs = endCal.timeInMillis
        }
        if (endMs < System.currentTimeMillis()) {
            endCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }
}