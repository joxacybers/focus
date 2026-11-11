package com.example.attention

import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class UsageStatsHelper(private val context: Context) {

    fun getDailyUsage(timeStart: Long, timeEnd: Long): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            timeStart,
            timeEnd
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

                // Specific exclusions (launchers)
                val excludeList = listOf("com.google.android.apps.nexuslauncher", "com.android.launcher", "com.sec.android.app.launcher", "com.miui.home")
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

    // Bugungi foydalanishni olish uchun shortcut
    fun getTodayUsage(): List<AppUsageInfo> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        return getDailyUsage(startTime, endTime)
    }

    fun getWeeklyScreenTime(): List<DailyStats> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Find the start of the week (Sunday as first day for standard charting)
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        calendar.add(Calendar.DAY_OF_YEAR, -(currentDayOfWeek - 1))

        val statsList = mutableListOf<DailyStats>()
        val sdf = SimpleDateFormat("EEE", Locale.ENGLISH) // Gets "Sun", "Mon"

        for (i in 0..6) {
            val start = calendar.timeInMillis
            val end = start + (24 * 60 * 60 * 1000L) - 1

            // Don't query future dates
            if (start > System.currentTimeMillis()) {
                val tempCal = Calendar.getInstance().apply { timeInMillis = start }
                val dayName = sdf.format(tempCal.time)
                statsList.add(DailyStats(dayName, 0L, start, end))
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                continue
            }

            // Query Exact Screen Interactive Time via UsageEvents for true screen time
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

            // If screen was still interactive at the end of the event loop
            if (isInteractive) {
                val lastTime = if (end < System.currentTimeMillis()) end else System.currentTimeMillis()
                totalTime += (lastTime - interactiveStart)
            }
            
            // Fallback if events are empty (e.g. some devices or missing data)
            if (totalTime == 0L) {
                val dailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                // Just sum the foreground time of apps as a rough estimate
                totalTime = dailyStats?.sumOf { it.totalTimeInForeground } ?: 0L
            }

            val tempCal = Calendar.getInstance().apply { timeInMillis = start }
            val dayName = sdf.format(tempCal.time) // "Sun", "Mon", "Tue"
            statsList.add(DailyStats(dayName, totalTime, start, end))

            calendar.add(Calendar.DAY_OF_YEAR, 1)
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
    val totalTimeMs: Long,
    val startTime: Long,
    val endTime: Long
)