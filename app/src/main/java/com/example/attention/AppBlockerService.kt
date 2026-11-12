package com.example.attention

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class AppBlockerService : AccessibilityService() {

    private lateinit var lockOverlayManager: LockOverlayManager
    private lateinit var preferencesManager: PreferencesManager

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var checkJob: Job? = null

    // Whitelisted apps that should NEVER be blocked, e.g., dialer
    private val whitelist = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.example.attention" // Don't block ourselves so we can change settings if not in global block
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        lockOverlayManager = LockOverlayManager(this)
        preferencesManager = PreferencesManager(this)

        // Start a continuous polling loop as a backup to accessibility events
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            checkAndBlock(packageName)
        }
    }

    private fun checkAndBlock(packageName: String) {
        val isGlobalBlock = preferencesManager.isGlobalBlockActive()
        val hasActiveSchedule = preferencesManager.getActiveSchedule() != null
        val isAppBlocked = preferencesManager.isAppBlocked(packageName)

        if (whitelist.contains(packageName)) {
            // Never block whitelisted apps
            if ((isGlobalBlock || hasActiveSchedule) && packageName == "com.example.attention") {
                // If focus or sleep is active, block even our own app
            } else {
                lockOverlayManager.hideOverlay()
                return
            }
        }

        if (isGlobalBlock || hasActiveSchedule || isAppBlocked) {
            Log.d("AppBlocker", "Blocking app: $packageName")
            lockOverlayManager.showOverlay()
            
            // Redirect to home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        } else {
            lockOverlayManager.hideOverlay()
        }
    }

    private fun startPolling() {
        checkJob?.cancel()
        checkJob = scope.launch {
            var wasScheduleActive = preferencesManager.getActiveSchedule() != null
            while (isActive) {
                val isScheduleActiveNow = preferencesManager.getActiveSchedule() != null
                
                // If schedule just started
                if (isScheduleActiveNow && !wasScheduleActive) {
                    lockOverlayManager.showOverlay()
                }
                // If schedule just ended
                if (!isScheduleActiveNow && wasScheduleActive && !preferencesManager.isGlobalBlockActive()) {
                    lockOverlayManager.hideOverlay()
                }
                
                wasScheduleActive = isScheduleActiveNow
                delay(1000)
            }
        }
    }

    override fun onInterrupt() {
        lockOverlayManager.hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        lockOverlayManager.hideOverlay()
    }
}