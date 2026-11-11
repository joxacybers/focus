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
}