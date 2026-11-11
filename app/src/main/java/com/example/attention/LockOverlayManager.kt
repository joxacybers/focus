package com.example.attention

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

import android.os.CountDownTimer
import java.util.Locale

class LockOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = PreferencesManager(context)
    private var overlayView: View? = null
    private var timer: CountDownTimer? = null

    // Secret exit state
    private var tapCount = 0
    private var lastTapTime = 0L

    fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER

        overlayView = View.inflate(context, R.layout.overlay_lock, null).apply {
            val taskNameText = findViewById<TextView>(R.id.task_name_text)
            val timerText = findViewById<TextView>(R.id.timer_text)
            val btnGiveUp = findViewById<Button>(R.id.btn_give_up)
            
            taskNameText.text = prefs.getCurrentTaskName()

            // Setup double tap to Give up
            btnGiveUp.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 1000) {
                    // Confirmed Give up
                    prefs.stopGlobalBlock()
                    hideOverlay()
                    lastTapTime = 0L
                } else {
                    btnGiveUp.text = "Tap again to confirm"
                    lastTapTime = now
                }
            }
            
            // Setup Timer
            val remainingMillis = prefs.getGlobalBlockEndTime() - System.currentTimeMillis()
            if (remainingMillis > 0) {
                timer = object : CountDownTimer(remainingMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val minutes = (millisUntilFinished / 1000) / 60
                        val seconds = (millisUntilFinished / 1000) % 60
                        timerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    }

                    override fun onFinish() {
                        prefs.stopGlobalBlock()
                        hideOverlay()
                    }
                }.start()
            } else {
                timerText.text = "00:00"
            }
        }

        windowManager.addView(overlayView, params)
    }

    fun hideOverlay() {
        if (overlayView != null) {
            timer?.cancel()
            timer = null
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }
}