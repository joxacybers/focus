package com.example.focus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.focus.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != ACTION_CHECK_SCHEDULES &&
            action != ACTION_WATCHDOG_CHECK
        ) {
            return
        }

        // Acquire a temporary wake lock so CPU does not sleep during schedule processing
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FocusLock:ScheduleReceiverWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(25_000L) // 25 seconds max
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                FocusRepository.init(context)
                com.example.focus.service.FocusForegroundService.start(context)
                FocusRepository.checkAndApplySchedules(context)
                FocusRepository.rescheduleAlarms(context)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_SCHEDULES = "com.example.focus.ACTION_CHECK_SCHEDULES"
        const val ACTION_WATCHDOG_CHECK = "com.example.focus.ACTION_WATCHDOG_CHECK"
        const val REQUEST_CODE = 4001
        const val WATCHDOG_REQUEST_CODE = 4002
    }
}
