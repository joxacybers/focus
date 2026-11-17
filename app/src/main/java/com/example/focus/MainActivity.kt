package com.example.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.focus.data.FocusRepository
import com.example.focus.service.FocusForegroundService
import com.example.focus.ui.lock.LockScreenActivity
import com.example.focus.ui.screens.FocusScreen
import com.example.focus.ui.theme.FocusTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusRepository.init(this)
        requestNotificationPermissionIfNeeded()

        // If a session was already active, ensure foreground service and LockScreen are running
        if (FocusRepository.state.value.isLocked) {
            FocusForegroundService.start(this)
            LockScreenActivity.launch(this)
        }

        enableEdgeToEdge()
        setContent {
            val state by FocusRepository.state.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (state.darkThemeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }

            FocusTheme(
                darkTheme = useDarkTheme,
                dynamicColor = state.dynamicColors
            ) {
                FocusMainApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FocusRepository.checkAndApplySchedules(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun FocusMainApp() {
    val context = LocalContext.current
    val state by FocusRepository.state.collectAsState()

    // Automatically launch LockScreenActivity when session becomes locked
    LaunchedEffect(state.isLocked) {
        if (state.isLocked) {
            LockScreenActivity.launch(context)
        }
    }

    FocusScreen(
        state = state,
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun FocusMainAppPreview() {
    FocusTheme {
        FocusMainApp()
    }
}
