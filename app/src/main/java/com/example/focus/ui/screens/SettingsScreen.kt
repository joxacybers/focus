package com.example.focus.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.focus.BuildConfig
import com.example.focus.data.FocusRepository
import com.example.focus.data.FocusState
import com.example.focus.update.GitHubUpdateManager
import com.example.focus.update.UpdateStatus
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: FocusState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateStatus by GitHubUpdateManager.status.collectAsState()

    var githubOwnerInput by remember(state.githubOwner) { mutableStateOf(state.githubOwner) }
    var githubRepoInput by remember(state.githubRepo) { mutableStateOf(state.githubRepo) }

    // Refresh permission statuses whenever returning from Android System Settings
    var permissionRefreshTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasOverlay = remember(permissionRefreshTrigger) { FocusRepository.hasOverlayPermission(context) }
    val hasAccessibility = remember(permissionRefreshTrigger) { FocusRepository.isAccessibilityServiceEnabled(context) }
    val hasDnd = remember(permissionRefreshTrigger) { FocusRepository.hasDndPermission(context) }
    val hasNotifListener = remember(permissionRefreshTrigger) { FocusRepository.isNotificationListenerEnabled(context) }
    val hasUsageStats = remember(permissionRefreshTrigger) { FocusRepository.hasUsageStatsPermission(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. GITHUB ONLINE AUTO-UPDATE CARD ("Yangilanishni tekshirish" tugmasi bilan)
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "GitHub Online Yangilanish",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Joriy versiya: v${BuildConfig.VERSION_NAME} (kod: ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Ilovani GitHub Releases orqali masofadan yangilash uchun repozitoriy manzilini kiriting:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = githubOwnerInput,
                        onValueChange = {
                            githubOwnerInput = it
                            FocusRepository.updateSettings(
                                githubOwner = it,
                                githubRepo = githubRepoInput
                            )
                        },
                        label = { Text("GitHub Egasi (Owner)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = githubRepoInput,
                        onValueChange = {
                            githubRepoInput = it
                            FocusRepository.updateSettings(
                                githubOwner = githubOwnerInput,
                                githubRepo = it
                            )
                        },
                        label = { Text("Repozitoriy (Repo)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Check for Updates Button
                Button(
                    onClick = {
                        FocusRepository.updateSettings(
                            githubOwner = githubOwnerInput,
                            githubRepo = githubRepoInput
                        )
                        scope.launch {
                            GitHubUpdateManager.checkForUpdates(
                                owner = githubOwnerInput,
                                repo = githubRepoInput
                            )
                        }
                    },
                    enabled = updateStatus !is UpdateStatus.Checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (updateStatus is UpdateStatus.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("GitHub'dan tekshirilmoqda...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Yangilanishni tekshirish",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Update Status Result Box
                when (val status = updateStatus) {
                    is UpdateStatus.Idle -> {}
                    is UpdateStatus.Checking -> {}
                    is UpdateStatus.UpToDate -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column {
                                    Text(
                                        text = "Sizda eng so'nggi versiya o'rnatilgan! (v${status.currentVersion})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    status.releaseInfo?.let { info ->
                                        Text(
                                            text = "So'nggi GitHub nashri: ${info.tagName} (${info.publishedAt})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is UpdateStatus.UpdateAvailable -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Yangi versiya mavjud: ${status.releaseInfo.tagName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = status.releaseInfo.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Button(
                                    onClick = {
                                        GitHubUpdateManager.downloadAndInstallApk(context, status.releaseInfo)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (status.releaseInfo.apkDownloadUrl != null)
                                            "APK yuklab olish va o'rnatish"
                                        else
                                            "GitHub Release sahifasidan yuklash"
                                    )
                                }
                            }
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = "Focus ${status.releaseInfo.tagName} yuklab olinmoqda... Tugagach o'rnatish oynasi ochiladi.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "APK yuklab olindi: ${status.releaseInfo.tagName}",
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { GitHubUpdateManager.installApk(context, status.apkFile) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Hozir o'rnatish")
                                }
                            }
                        }
                    }
                    is UpdateStatus.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val url = "https://github.com/${githubOwnerInput.trim()}/${githubRepoInput.trim()}/releases"
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        } catch (_: Exception) {
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("GitHub sahifasini ochish")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. SYSTEM PERMISSIONS & FULL-SCREEN LOCK ACCESS
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Tizim ruxsatlari (To'liq bloklash uchun)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Control Panel, bildirishnoma va boshqa ilovalarni bloklash uchun yoqing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PermissionItemRow(
                    icon = Icons.Default.AccessibilityNew,
                    title = "Maxsus imkoniyatlar (Accessibility)",
                    subtitle = "Boshqa ilovalarni va Control Panel pardasini darhol yopadi",
                    isGranted = hasAccessibility,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                HorizontalDivider()

                PermissionItemRow(
                    icon = Icons.Default.Layers,
                    title = "Ustki oyna ruxsati (Overlay)",
                    subtitle = "Tepadagi status bar va bildirishnomalar pardasini to'sib turadi",
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

                PermissionItemRow(
                    icon = Icons.Default.DoNotDisturbOn,
                    title = "Bezovta qilinmasin (Do Not Disturb)",
                    subtitle = "O'qish vaqtida ovoz va qo'ng'iroq/xabar tovushlarini o'chiradi",
                    isGranted = hasDnd,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                )

                HorizontalDivider()

                PermissionItemRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Bildirishnomalar filtri (Notification Listener)",
                    subtitle = "Kelgan chalg'ituvchi bildirishnomalarni avtomatik tozalaydi",
                    isGranted = hasNotifListener,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )

                HorizontalDivider()

                PermissionItemRow(
                    icon = Icons.Default.QueryStats,
                    title = "Foydalanish statistikasi (Usage Access)",
                    subtitle = "Ruxsat etilmagan ilova ochilganini aniqlab blok ekraniga qaytaradi",
                    isGranted = hasUsageStats,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
        }

        // 3. FOCUS & STUDY LOCK PREFERENCES
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Qulflash va Himoya Sozlamalari",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                SettingSwitchRow(
                    title = "Qat'iy rejim (Strict Mode)",
                    subtitle = "Vaqt tugamaguncha qulfni oddiy bosish bilan ochib bo'lmaydi (matematik misol talab qilinadi)",
                    checked = state.strictMode,
                    onCheckedChange = { FocusRepository.updateSettings(strictMode = it) }
                )

                HorizontalDivider()

                SettingSwitchRow(
                    title = "Control Panel va Status Barni bloklash",
                    subtitle = "Tepadan tushadigan boshqaruv panelini to'sib, soat, antenna va zaryadni blok ekranida ko'rsatadi",
                    checked = state.blockControlPanel,
                    onCheckedChange = { FocusRepository.updateSettings(blockControlPanel = it) }
                )

                HorizontalDivider()

                SettingSwitchRow(
                    title = "Ovoz va bildirishnomalarni o'chirish",
                    subtitle = "Focus va Study boshlanganda telefonni avtomatik Sukut (DND) rejimiga o'tkazadi",
                    checked = state.autoDndAndMute,
                    onCheckedChange = { FocusRepository.updateSettings(autoDndAndMute = it) }
                )

                HorizontalDivider()

                SettingSwitchRow(
                    title = "Blok ekranida ekranni o'chmas qilish",
                    subtitle = "Soat va taymer doimo ko'rinib turishi uchun ekran yoniq qoladi",
                    checked = state.keepScreenAwake,
                    onCheckedChange = { FocusRepository.updateSettings(keepScreenAwake = it) }
                )

                HorizontalDivider()

                SettingSwitchRow(
                    title = "Pomodoro tanaffusida ham qulfni saqlash",
                    subtitle = "Qisqa tanaffus vaqtida ham chalg'ituvchi ilovalarga kirishni cheklaydi",
                    checked = state.lockDuringPomodoroBreak,
                    onCheckedChange = { FocusRepository.updateSettings(lockDuringPomodoroBreak = it) }
                )

                HorizontalDivider()

                SettingSwitchRow(
                    title = "Ekranni mixlash (Screen Pinning / Kiosk)",
                    subtitle = "Android tizim darajasida Home va Recents tugmalarini qulflaydi",
                    checked = state.useScreenPinning,
                    onCheckedChange = { FocusRepository.updateSettings(useScreenPinning = it) }
                )
            }
        }

        // 4. MATERIAL DESIGN 3 APPEARANCE SETTINGS
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Material Design 3 Ko'rinishi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                SettingSwitchRow(
                    title = "Material You Dinamik Ranglar",
                    subtitle = "Telefon fon rasmidan kelib chiqib Material 3 ranglarini moslashtiradi",
                    checked = state.dynamicColors,
                    onCheckedChange = { FocusRepository.updateSettings(dynamicColors = it) }
                )

                Text(
                    text = "Ilova mavzusi:",
                    style = MaterialTheme.typography.labelLarge
                )
                val themeModes = listOf(0 to "Avto (Tizim)", 1 to "Yorug'", 2 to "Qorong'u")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeModes.forEachIndexed { idx, (modeValue, label) ->
                        SegmentedButton(
                            selected = state.darkThemeMode == modeValue,
                            onClick = { FocusRepository.updateSettings(darkThemeMode = modeValue) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = themeModes.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isGranted) {
            FilledTonalButton(onClick = onClick) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Faol")
            }
        } else {
            Button(onClick = onClick) {
                Text("Yoqish")
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
