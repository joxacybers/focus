package com.example.focus.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.focus.BuildConfig
import com.example.focus.data.FocusRepository
import com.example.focus.data.FocusSchedule
import com.example.focus.data.FocusState
import com.example.focus.data.SessionMode
import com.example.focus.ui.lock.LockScreenActivity
import com.example.focus.update.GitHubUpdateManager
import com.example.focus.update.UpdateStatus
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    state: FocusState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Recheck permissions on resume
    var permissionTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasOverlay = remember(permissionTrigger) { FocusRepository.hasOverlayPermission(context) }
    val hasAccessibility = remember(permissionTrigger) { FocusRepository.isAccessibilityServiceEnabled(context) }
    val hasDnd = remember(permissionTrigger) { FocusRepository.hasDndPermission(context) }
    val hasNotifListener = remember(permissionTrigger) { FocusRepository.isNotificationListenerEnabled(context) }
    val hasUsageStats = remember(permissionTrigger) { FocusRepository.hasUsageStatsPermission(context) }
    val hasBatteryOptimization = remember(permissionTrigger) { FocusRepository.isIgnoringBatteryOptimizations(context) }
    val hasExactAlarm = remember(permissionTrigger) { FocusRepository.canScheduleExactAlarms(context) }
    val hasDeviceAdmin = remember(permissionTrigger) { FocusRepository.isDeviceAdminActive(context) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "FocusLock",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Sozlamalar va Ruxsatlar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddScheduleDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Yangi vaqt oralig'i qo'shish",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. ACTIVE SESSION BANNER (agar hozir qulflangan bo'lsa)
            if (state.isLocked) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Bloklash davom etmoqda",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.sessionTitle} • Qolgan: ${state.remainingSeconds / 60}d ${state.remainingSeconds % 60}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        FilledTonalButton(
                            onClick = { LockScreenActivity.launch(context) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Blok ekrani")
                        }
                    }
                }
            }

            // 2. PERMISSION BANNER (agar ruxsatlar to'liq berilmagan bo'lsa)
            if (!hasOverlay || !hasAccessibility || !hasDnd) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettingsSheet = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Tizim ruxsatlari kerak",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "To'liq qulflash uchun Sozlamalardan ruxsat bering",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = { showSettingsSheet = true }) {
                            Text("Sozlash", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. SECTION HEADER (Intervalli bloklash jadvallari)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Intervalli bloklash jadvallari",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${state.schedules.size} ta",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 4. SCHEDULES LIST / EMPTY STATE
            if (state.schedules.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Hozircha intervalli jadvallar yo'q",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Belgilangan vaqt oralig'ida telefonni qulflash uchun pastdagi + tugmasini bosing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                state.schedules.forEach { schedule ->
                    CompactScheduleItemRow(
                        schedule = schedule,
                        onToggle = { enabled -> FocusRepository.toggleSchedule(schedule.id, enabled) },
                        onStartNow = {
                            val startTotal = schedule.startHour * 60 + schedule.startMinute
                            val endTotal = schedule.endHour * 60 + schedule.endMinute
                            val duration = if (endTotal > startTotal) {
                                endTotal - startTotal
                            } else {
                                (24 * 60 - startTotal) + endTotal
                            }
                            FocusRepository.startFocusSession(
                                context = context,
                                durationMinutes = duration.coerceAtLeast(5),
                                title = schedule.title,
                                mode = SessionMode.FOCUS
                            )
                        },
                        onDelete = { FocusRepository.deleteSchedule(schedule.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    if (showAddScheduleDialog) {
        EnhancedScheduleDialog(
            onDismiss = { showAddScheduleDialog = false },
            onAdd = { schedule ->
                FocusRepository.addSchedule(schedule)
                showAddScheduleDialog = false
            }
        )
    }

    if (showSettingsSheet) {
        FocusSettingsBottomSheet(
            state = state,
            hasOverlay = hasOverlay,
            hasAccessibility = hasAccessibility,
            hasDnd = hasDnd,
            hasNotifListener = hasNotifListener,
            hasUsageStats = hasUsageStats,
            hasBatteryOptimization = hasBatteryOptimization,
            hasExactAlarm = hasExactAlarm,
            hasDeviceAdmin = hasDeviceAdmin,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

/**
 * Compact, modern grayscale row displaying a scheduled focus interval
 */
@Composable
private fun CompactScheduleItemRow(
    schedule: FocusSchedule,
    onToggle: (Boolean) -> Unit,
    onStartNow: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top row: Title + Time + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%02d:%02d – %02d:%02d",
                            schedule.startHour,
                            schedule.startMinute,
                            schedule.endHour,
                            schedule.endMinute
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (schedule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle
                )
            }

            // Bottom row: Repeat days description + Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val daysLabel = formatScheduleDays(schedule.daysOfWeek)
                    Text(
                        text = daysLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onStartNow,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Hozir boshlash",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Boshlash", style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "O'chirish",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Settings & Permissions Modal Bottom Sheet (Online Update, System Permissions, Preferences)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusSettingsBottomSheet(
    state: FocusState,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    hasDnd: Boolean,
    hasNotifListener: Boolean,
    hasUsageStats: Boolean,
    hasBatteryOptimization: Boolean,
    hasExactAlarm: Boolean,
    hasDeviceAdmin: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateStatus by GitHubUpdateManager.status.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var githubOwnerInput by remember(state.githubOwner) { mutableStateOf(state.githubOwner) }
    var githubRepoInput by remember(state.githubRepo) { mutableStateOf(state.githubRepo) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Sozlamalar va Ruxsatlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Yopish")
                }
            }

            // 1. GITHUB ONLINE UPDATE CARD
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = "GitHub Online Yangilanish",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Joriy versiya: v${BuildConfig.VERSION_NAME} (kod: ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = githubOwnerInput,
                            onValueChange = {
                                githubOwnerInput = it
                                FocusRepository.updateSettings(githubOwner = it, githubRepo = githubRepoInput)
                            },
                            label = { Text("Owner") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = githubRepoInput,
                            onValueChange = {
                                githubRepoInput = it
                                FocusRepository.updateSettings(githubOwner = githubOwnerInput, githubRepo = it)
                            },
                            label = { Text("Repo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            FocusRepository.updateSettings(githubOwner = githubOwnerInput, githubRepo = githubRepoInput)
                            scope.launch {
                                GitHubUpdateManager.checkForUpdates(owner = githubOwnerInput, repo = githubRepoInput)
                            }
                        },
                        enabled = updateStatus !is UpdateStatus.Checking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (updateStatus is UpdateStatus.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tekshirilmoqda...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yangilanishni tekshirish", fontWeight = FontWeight.Bold)
                        }
                    }

                    when (val status = updateStatus) {
                        is UpdateStatus.UpToDate -> {
                            Text(
                                text = "Sizda eng so'nggi versiya o'rnatilgan! (v${status.currentVersion})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        is UpdateStatus.UpdateAvailable -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Yangi versiya chiqdi: ${status.releaseInfo.tagName}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Button(
                                        onClick = { GitHubUpdateManager.downloadAndInstallApk(context, status.releaseInfo) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Yuklab olish va o'rnatish")
                                    }
                                }
                            }
                        }
                        is UpdateStatus.Downloading -> {
                            Text(
                                text = "Yuklab olinmoqda... Tugagach o'rnatish oynasi ochiladi.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        is UpdateStatus.ReadyToInstall -> {
                            Button(
                                onClick = { GitHubUpdateManager.installApk(context, status.apkFile) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("O'rnatishni boshlash")
                            }
                        }
                        is UpdateStatus.Error -> {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            }

            // 2. SYSTEM PERMISSIONS SECTION
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Tizim ruxsatlari",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PermissionRow(
                        icon = Icons.Default.AccessibilityNew,
                        title = "Maxsus imkoniyatlar (Accessibility)",
                        subtitle = "Boshqa ilovalarni va Control Panel pardasini yopadi",
                        isGranted = hasAccessibility,
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        icon = Icons.Default.Layers,
                        title = "Ustki oyna (Overlay)",
                        subtitle = "Tepadagi status bar va Control Panelni to'sib turadi",
                        isGranted = hasOverlay,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:${context.packageName}".toUri()
                                )
                            )
                        }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        icon = Icons.Default.DoNotDisturbOn,
                        title = "Bezovta qilinmasin (DND)",
                        subtitle = "Qulf vaqtida tovush va xabarlarni ovozsiz qiladi",
                        isGranted = hasDnd,
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Bildirishnomalar filtri",
                        subtitle = "Chalg'ituvchi bildirishnomalarni avtomatik tozalaydi",
                        isGranted = hasNotifListener,
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        icon = Icons.Default.QueryStats,
                        title = "Foydalanish statistikasi (Usage Access)",
                        subtitle = "Begona ilova ochilganda darhol blok ekraniga qaytaradi",
                        isGranted = hasUsageStats,
                        onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Batareya tejamkorligini o'chirish (Fon rejimi)",
                        subtitle = "Ilova yopilganda ham rejalashtirilgan vaqtda aniq ishlashi uchun",
                        isGranted = hasBatteryOptimization,
                        onClick = { FocusRepository.requestIgnoreBatteryOptimizations(context) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        icon = Icons.Default.Alarm,
                        title = "Aniq taymerlar va signallar (Exact Alarms)",
                        subtitle = "Vaqt intervallari kechikmasdan o'z vaqtida qulflanishi uchun",
                        isGranted = hasExactAlarm,
                        onClick = { FocusRepository.requestExactAlarmPermission(context) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        icon = Icons.Default.Security,
                        title = "Qurilma administratori (Anti-Uninstall)",
                        subtitle = "Bloklangan vaqtda ilovani o'chirishni (Uninstall) taqiqlaydi",
                        isGranted = hasDeviceAdmin,
                        onClick = { FocusRepository.requestDeviceAdmin(context) }
                    )
                }
            }

            // 3. LOCK PREFERENCES
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Qulflash parametrlari",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    SettingSwitch(
                        title = "Qat'iy rejim (Strict Mode)",
                        subtitle = "Faqat matematik misol yechib chiqish mumkin",
                        checked = state.strictMode,
                        onCheckedChange = { FocusRepository.updateSettings(strictMode = it) }
                    )
                    HorizontalDivider()
                    SettingSwitch(
                        title = "Control Panelni to'sish",
                        subtitle = "Tepadan tushadigan boshqaruv panelini bloklaydi",
                        checked = state.blockControlPanel,
                        onCheckedChange = { FocusRepository.updateSettings(blockControlPanel = it) }
                    )
                    HorizontalDivider()
                    SettingSwitch(
                        title = "Avtomatik ovozsiz (DND) rejim",
                        subtitle = "Qulflanganda tovushlarni o'chiradi",
                        checked = state.autoDndAndMute,
                        onCheckedChange = { FocusRepository.updateSettings(autoDndAndMute = it) }
                    )
                    HorizontalDivider()
                    SettingSwitch(
                        title = "Ekranni doim yoniq saqlash",
                        subtitle = "Soat va taymer doimo ko'rinib turishi uchun",
                        checked = state.keepScreenAwake,
                        onCheckedChange = { FocusRepository.updateSettings(keepScreenAwake = it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isGranted) {
            FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Faol", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Button(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
                Text("Yoqish", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Enhanced dialog to configure Schedule name, time range via Android default TimePickerDialog, and weekday recurrence
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedScheduleDialog(
    onDismiss: () -> Unit,
    onAdd: (FocusSchedule) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("Ish va o'qish vaqti") }
    var startHour by remember { mutableIntStateOf(9) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(12) }
    var endMinute by remember { mutableIntStateOf(0) }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) } // 1=Mon .. 7=Sun

    val showAndroidTimePicker = { initialHour: Int, initialMinute: Int, onTimeSelected: (Int, Int) -> Unit ->
        val is24 = DateFormat.is24HourFormat(context)
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onTimeSelected(hourOfDay, minute)
            },
            initialHour,
            initialMinute,
            is24
        ).show()
    }

    val dayNames = listOf(
        1 to "Du",
        2 to "Se",
        3 to "Chor",
        4 to "Pay",
        5 to "Ju",
        6 to "Sha",
        7 to "Yak"
    )

    // Calculate total duration in minutes
    val startTotalMinutes = startHour * 60 + startMinute
    val endTotalMinutes = endHour * 60 + endMinute
    val durationMinutes = if (endTotalMinutes >= startTotalMinutes) {
        endTotalMinutes - startTotalMinutes
    } else {
        (24 * 60 - startTotalMinutes) + endTotalMinutes
    }
    val durHours = durationMinutes / 60
    val durMins = durationMinutes % 60
    val durationText = buildString {
        append("Davomiyligi: ")
        if (durHours > 0) append("$durHours soat ")
        if (durMins > 0 || durHours == 0) append("$durMins daqiqa")
    }.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Intervalli jadval qo'shish", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Jadval nomi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Time Range Section
                Text(
                    text = "Vaqt oralig'i (O'zgartirish uchun bosing):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Visual interactive time cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeDisplayCard(
                        label = "Boshlanish",
                        hour = startHour,
                        minute = startMinute,
                        onClick = {
                            showAndroidTimePicker(startHour, startMinute) { h, m ->
                                startHour = h
                                startMinute = m
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text("—", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TimeDisplayCard(
                        label = "Tugash",
                        hour = endHour,
                        minute = endMinute,
                        onClick = {
                            showAndroidTimePicker(endHour, endMinute) { h, m ->
                                endHour = h
                                endMinute = m
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Duration badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⏱ $durationText",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Quick presets: +30m, +1h, +2h, +3h, +4h
                Text(
                    text = "Tezkor davomiylik qo'shish:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        30 to "+30 daq",
                        60 to "+1 soat",
                        120 to "+2 soat",
                        180 to "+3 soat",
                        240 to "+4 soat"
                    ).forEach { (addedMins, label) ->
                        AssistChip(
                            onClick = {
                                val total = (startHour * 60 + startMinute + addedMins) % (24 * 60)
                                endHour = total / 60
                                endMinute = total % 60
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Weekday Recurrence Section
                Text(
                    text = "Qaytarish (Hafta kunlari):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Quick presets: Ish kunlari, Dam olish, Har kuni
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { selectedDays = setOf(1, 2, 3, 4, 5) },
                        label = { Text("Ish kunlari", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    AssistChip(
                        onClick = { selectedDays = setOf(6, 7) },
                        label = { Text("Dam olish", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    AssistChip(
                        onClick = { selectedDays = setOf(1, 2, 3, 4, 5, 6, 7) },
                        label = { Text("Har kuni", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Interactive 7-day chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dayNames.forEach { (dayIndex, name) ->
                        val isSelected = dayIndex in selectedDays
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        if (selectedDays.size > 1) selectedDays - dayIndex else selectedDays
                                    } else {
                                        selectedDays + dayIndex
                                    }
                                }
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        FocusSchedule(
                            id = UUID.randomUUID().toString(),
                            title = title.ifBlank { "FocusLock jadvali" },
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            enabled = true,
                            daysOfWeek = selectedDays
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Saqlash")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor qilish")
            }
        }
    )
}

/**
 * Clickable card displaying hour and minute with clock icon
 */
@Composable
private fun TimeDisplayCard(
    label: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = String.format(Locale.US, "%02d:%02d", hour, minute),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "O'zgartirish",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}



private fun formatScheduleDays(days: Set<Int>): String {
    return when {
        days.size == 7 -> "Har kuni"
        days == setOf(1, 2, 3, 4, 5) -> "Ish kunlari (Du–Ju)"
        days == setOf(6, 7) -> "Dam olish kunlari (Sha, Yak)"
        days.isEmpty() -> "Tanlanmagan"
        else -> {
            val names = mapOf(
                1 to "Du",
                2 to "Se",
                3 to "Chor",
                4 to "Pay",
                5 to "Ju",
                6 to "Sha",
                7 to "Yak"
            )
            days.sorted().joinToString(", ") { names[it] ?: "" }
        }
    }
}
