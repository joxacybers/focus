package com.example.focus.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.focus.data.FocusRepository
import com.example.focus.data.PomodoroPhase
import com.example.focus.data.SessionMode
import com.example.focus.ui.lock.LockScreenActivity

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FocusAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        FocusRepository.init(this)
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        FocusRepository.checkAndApplySchedules(this)
        val state = FocusRepository.state.value
        if (!state.isLocked) return
        if (event == null) return

        // If Pomodoro is in break phase and break lock is off, allow normal use during break
        if (state.sessionMode == SessionMode.STUDY_POMODORO &&
            state.pomodoroPhase != PomodoroPhase.WORK &&
            !state.lockDuringPomodoroBreak
        ) {
            return
        }

        val pkgName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // 1. Block Control Panel / Notification Shade / Quick Settings when pulled down
        if (state.blockControlPanel && pkgName == "com.android.systemui") {
            val lowerClass = className.lowercase()
            val isShadeOrQuickSettings = lowerClass.contains("shade") ||
                lowerClass.contains("quicksettings") ||
                lowerClass.contains("notificationpanel") ||
                lowerClass.contains("statusbar") ||
                lowerClass.contains("qs") ||
                lowerClass.contains("recents")

            if (isShadeOrQuickSettings) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                LockScreenActivity.launch(this)
                return
            }
        }

        // 2. Anti-bypass: Block settings & app uninstaller during active lock
        val isSettingsOrInstaller = pkgName == "com.android.settings" ||
            pkgName == "com.google.android.packageinstaller" ||
            pkgName == "com.android.packageinstaller" ||
            pkgName.contains("securitycenter")

        if (isSettingsOrInstaller) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            LockScreenActivity.launch(this)
            return
        }

        // 3. Enforce app whitelist: ONLY FocusLock, Phone/Dialer, and Messages are allowed
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Ignore system input method / keyboard overlays
            if (pkgName.contains("inputmethod") ||
                pkgName.contains("keyboard") ||
                pkgName == "com.google.android.inputmethod.latin" ||
                pkgName == "com.samsung.android.honeyboard"
            ) {
                return
            }

            val allowedPackages = FocusRepository.getAllowedPackages(this)
            if (pkgName !in allowedPackages) {
                // If user opened Instagram, TikTok, YouTube, Games, Browser etc.
                // Immediately bring LockScreenActivity to the front!
                FocusRepository.grantTemporaryAllowedPackage(null)
                if (state.blockControlPanel) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }
                LockScreenActivity.launch(this)
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }
}
