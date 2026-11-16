package com.example.focus.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.focus.data.FocusRepository

class FocusNotificationListenerService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        FocusRepository.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val state = FocusRepository.state.value
        if (!state.isLocked || !state.autoDndAndMute) return

        val pkg = notification.packageName ?: return

        // Never cancel our own foreground notification or incoming phone calls / SMS
        if (pkg == packageName) return
        val category = notification.notification?.category
        if (category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_ALARM) {
            return
        }

        val allowedPackages = FocusRepository.getAllowedPackages(this)
        if (pkg in allowedPackages) {
            return
        }

        // Cancel all other distracting notifications while Focus / Study lock is active
        try {
            cancelNotification(notification.key)
        } catch (_: Exception) {
        }
    }
}
