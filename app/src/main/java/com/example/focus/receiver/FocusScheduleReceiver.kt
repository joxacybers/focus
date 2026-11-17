package com.example.focus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.focus.data.FocusRepository

class FocusScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != ACTION_CHECK_SCHEDULES
        ) {
            return
        }

        FocusRepository.init(context)
        FocusRepository.checkAndApplySchedules(context)
        FocusRepository.rescheduleAlarms(context)
    }

    companion object {
        const val ACTION_CHECK_SCHEDULES = "com.example.focus.ACTION_CHECK_SCHEDULES"
        const val REQUEST_CODE = 4001
    }
}
