package com.example.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.focus.data.FocusRepository
import com.example.focus.service.FocusForegroundService
import com.example.focus.ui.lock.LockScreenActivity
import com.example.focus.ui.screens.FocusScreen
import com.example.focus.ui.screens.SettingsScreen
import com.example.focus.ui.screens.StudyScreen
import com.example.focus.ui.theme.FocusTheme

private enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FOCUS("Focus", Icons.Filled.Lock, Icons.Outlined.Lock),
    STUDY("Study", Icons.Filled.School, Icons.Outlined.School),
    SETTINGS("Sozlamalar", Icons.Filled.Settings, Icons.Outlined.Settings)
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusMainApp() {
    val context = LocalContext.current
    val state by FocusRepository.state.collectAsState()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = MainTab.entries

    // Automatically launch LockScreenActivity when session becomes locked
    LaunchedEffect(state.isLocked) {
        if (state.isLocked) {
            LockScreenActivity.launch(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = tabs[selectedTabIndex].selectedIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when (tabs[selectedTabIndex]) {
                                MainTab.FOCUS -> "Focus"
                                MainTab.STUDY -> "Focus • Study"
                                MainTab.SETTINGS -> "Sozlamalar"
                            },
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    if (state.hasFlashUnit) {
                        IconButton(onClick = { FocusRepository.toggleTorch() }) {
                            Icon(
                                imageVector = if (state.isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Fonor",
                                tint = if (state.isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTabIndex == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (tabs[selectedTabIndex]) {
            MainTab.FOCUS -> FocusScreen(
                state = state,
                onNavigateToSettings = { selectedTabIndex = MainTab.SETTINGS.ordinal },
                modifier = Modifier.padding(innerPadding)
            )
            MainTab.STUDY -> StudyScreen(
                state = state,
                modifier = Modifier.padding(innerPadding)
            )
            MainTab.SETTINGS -> SettingsScreen(
                state = state,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FocusMainAppPreview() {
    FocusTheme {
        FocusMainApp()
    }
}
