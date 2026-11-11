package com.example.attention

import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class UsageStatsHelper(private val context: Context) {

    fun getDailyUsage(): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val packageManager = context.packageManager

        return usageStatsList
            .filter { it.totalTimeInForeground > 0 }
            .mapNotNull { stats ->
                // Filter out our own app
                if (stats.packageName == context.packageName) return@mapNotNull null

                // Filter out system apps/services by checking if they have a launcher intent
                val launchIntent = packageManager.getLaunchIntentForPackage(stats.packageName)
                if (launchIntent == null) return@mapNotNull null

                // Specific exclusions (launchers, digital wellbeing, etc.)
                val excludeList = listOf("com.google.android.apps.nexuslauncher", "com.android.launcher", "com.sec.android.app.launcher")
                if (excludeList.contains(stats.packageName)) return@mapNotNull null

                try {
                    val appInfo = packageManager.getApplicationInfo(stats.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    AppUsageInfo(stats.packageName, appName, stats.totalTimeInForeground)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.totalTimeInForeground }
    }

    fun getWeeklyUsage(): List<DailyStats> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val statsList = mutableListOf<DailyStats>()
        val sdf = SimpleDateFormat("EE", Locale.ENGLISH) // Gets "Mon", "Tue"

        for (i in 6 downTo 0) {
            val start = calendar.timeInMillis - (i * 24 * 60 * 60 * 1000L)
            val end = start + (24 * 60 * 60 * 1000L) - 1

            // Query Exact Screen Interactive Time
            val events = usageStatsManager.queryEvents(start, end)
            var totalTime = 0L
            var interactiveStart = 0L
            var isInteractive = false
            
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE) {
                    isInteractive = true
                    interactiveStart = event.timeStamp
                } else if (event.eventType == android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    if (isInteractive) {
                        totalTime += (event.timeStamp - interactiveStart)
                        isInteractive = false
                    }
                }
            }
            // If screen was still interactive at the end of the day bounds
            if (isInteractive) {
                totalTime += (end - interactiveStart)
            }
            
            // Fallback just in case
            if (totalTime == 0L) {
                val dailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                totalTime = dailyStats?.sumOf { it.totalTimeInForeground } ?: 0L
            }

            val tempCal = Calendar.getInstance().apply { timeInMillis = start }
            val dayName = sdf.format(tempCal.time).take(2) // "Mo", "Tu", "We"
            statsList.add(DailyStats(dayName, totalTime))
        }
        return statsList
    }
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long
)

data class DailyStats(
    val dayName: String,
    val totalTimeMs: Long
)