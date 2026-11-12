package com.example.attention

import android.content.Context

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("attention_prefs", Context.MODE_PRIVATE)

    fun isAppBlocked(packageName: String): Boolean {
        // Retrieve the set of blocked packages, default empty
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        return blockedApps.contains(packageName)
    }

    fun setAppBlocked(packageName: String, blocked: Boolean) {
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (blocked) {
            blockedApps.add(packageName)
        } else {
            blockedApps.remove(packageName)
        }
        prefs.edit().putStringSet("blocked_apps", blockedApps).apply()
    }

    fun getAllBlockedApps(): Set<String> {
        return prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
    }

    fun isGlobalBlockActive(): Boolean {
        val blockEndTime = prefs.getLong("global_block_end_time", 0L)
        return System.currentTimeMillis() < blockEndTime
    }

    fun getGlobalBlockEndTime(): Long {
        return prefs.getLong("global_block_end_time", 0L)
    }

    fun getGlobalBlockStartTime(): Long {
        return prefs.getLong("global_block_start_time", 0L)
    }

    fun getCurrentTaskName(): String {
        return prefs.getString("global_block_task_name", "Focusing") ?: "Focusing"
    }

    fun startGlobalBlock(durationMillis: Long, taskName: String) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis
        prefs.edit()
            .putLong("global_block_start_time", startTime)
            .putLong("global_block_end_time", endTime)
            .putString("global_block_task_name", taskName)
            .apply()
    }

    fun stopGlobalBlock() {
        prefs.edit()
            .putLong("global_block_start_time", 0L)
            .putLong("global_block_end_time", 0L)
            .putString("global_block_task_name", "")
            .apply()
    }

    // --- SLEEP MODE LOGIC ---
    fun isSleepModeEnabled(): Boolean = prefs.getBoolean("sleep_mode_enabled", false)
    
    fun setSleepModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sleep_mode_enabled", enabled).apply()
    }

    fun setSleepStart(hour: Int, minute: Int) {
        prefs.edit().putInt("sleep_start_hour", hour).putInt("sleep_start_minute", minute).apply()
    }

    fun setSleepEnd(hour: Int, minute: Int) {
        prefs.edit().putInt("sleep_end_hour", hour).putInt("sleep_end_minute", minute).apply()
    }

    fun getSleepStartHour(): Int = prefs.getInt("sleep_start_hour", 22)
    fun getSleepStartMinute(): Int = prefs.getInt("sleep_start_minute", 30)
    
    fun getSleepEndHour(): Int = prefs.getInt("sleep_end_hour", 7)
    fun getSleepEndMinute(): Int = prefs.getInt("sleep_end_minute", 0)

    fun isCurrentlyInSleepMode(): Boolean {
        if (!isSleepModeEnabled()) return false
        
        val cal = java.util.Calendar.getInstance()
        val current = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = getSleepStartHour() * 60 + getSleepStartMinute()
        val end = getSleepEndHour() * 60 + getSleepEndMinute()

        return if (start < end) {
            current in start..end
        } else {
            current >= start || current < end
        }
    }

    fun getSleepModeStartTimeMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        val current = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = getSleepStartHour() * 60 + getSleepStartMinute()
        val end = getSleepEndHour() * 60 + getSleepEndMinute()

        cal.set(java.util.Calendar.HOUR_OF_DAY, getSleepStartHour())
        cal.set(java.util.Calendar.MINUTE, getSleepStartMinute())
        cal.set(java.util.Calendar.SECOND, 0)
        
        if (start > end && current < end) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1) // Started yesterday
        }
        return cal.timeInMillis
    }

    fun getSleepModeEndTimeMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        val current = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val end = getSleepEndHour() * 60 + getSleepEndMinute()
        
        cal.set(java.util.Calendar.HOUR_OF_DAY, getSleepEndHour())
        cal.set(java.util.Calendar.MINUTE, getSleepEndMinute())
        cal.set(java.util.Calendar.SECOND, 0)
        
        if (current > end) { 
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1) // Ends tomorrow
        }
        return cal.timeInMillis
    }
}