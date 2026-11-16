package com.example.focus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focus.data.FocusRepository
import com.example.focus.data.FocusSchedule
import com.example.focus.data.FocusState
import com.example.focus.data.SessionMode
import com.example.focus.ui.lock.LockScreenActivity
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    state: FocusState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMinutes by remember { mutableIntStateOf(30) }
    var sessionTitle by remember { mutableStateOf("Chuqur diqqat") }
    var showAddScheduleDialog by remember { mutableStateOf(false) }

    val hasOverlay = FocusRepository.hasOverlayPermission(context)
    val hasAccessibility = FocusRepository.isAccessibilityServiceEnabled(context)
    val hasDnd = FocusRepository.hasDndPermission(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active session banner if currently locked
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
                        .padding(14.dp),
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
                                text = "Qulflash faol",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "${state.sessionTitle} • Qolgan: ${state.remainingSeconds / 60}d ${state.remainingSeconds % 60}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { LockScreenActivity.launch(context) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekran", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Permission reminder banner (compact)
        if (!hasOverlay || !hasAccessibility || !hasDnd) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSettings)
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
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "To'liq bloklash uchun ruxsatlar kerak",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Control Panel va ilovalarni to'sish uchun sozlang",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onNavigateToSettings) {
                        Text("Yoqish", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 1. COMPACT "TELEFONNI VAQTINCHA QULFLASH" SECTION
        // -------------------------------------------------------------
        ElevatedCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row
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
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Vaqtincha qulflash",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Faqat Telefon, Fonor va Xabarlar ruxsat etiladi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Compact Duration Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val hours = selectedMinutes / 60
                        val mins = selectedMinutes % 60
                        val badgeText = if (hours > 0) {
                            if (mins > 0) "${hours}s ${mins}d" else "${hours} soat"
                        } else {
                            "$selectedMinutes daq"
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Compact Title Field
                OutlinedTextField(
                    value = sessionTitle,
                    onValueChange = { sessionTitle = it },
                    label = { Text("Sessiya maqsadi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Compact Slider Row
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = selectedMinutes.toFloat(),
                        onValueChange = { selectedMinutes = it.roundToInt().coerceAtLeast(1) },
                        valueRange = 1f..180f,
                        modifier = Modifier.height(28.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1 daqiqa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "3 soat (180 daq)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Presets FlowRow
                val presets = listOf(
                    3 to "3d",
                    15 to "15d",
                    25 to "25d",
                    45 to "45d",
                    60 to "1 soat",
                    120 to "2 soat"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { (mins, label) ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Compact Action Button
                Button(
                    onClick = {
                        FocusRepository.startFocusSession(
                            context = context,
                            durationMinutes = selectedMinutes,
                            title = sessionTitle.ifBlank { "Focus rejimi" },
                            mode = SessionMode.FOCUS
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Qulflashni boshlash ($selectedMinutes daq)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // ALLOWED APPS CHIPS (Telefon, Fonor, Xabarlar)
        // -------------------------------------------------------------
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ruxsat etilgan vositalar:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { FocusRepository.launchDialerApp(context) },
                        label = { Text("Telefon", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    AssistChip(
                        onClick = { FocusRepository.toggleTorch() },
                        label = { Text("Fonor", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.FlashlightOn, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    AssistChip(
                        onClick = { FocusRepository.launchMessagesApp(context) },
                        label = { Text("SMS", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 2. ENHANCED & COMPACT SCHEDULES SECTION (Vaqt jadvallari)
        // -------------------------------------------------------------
        ElevatedCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section Title Row
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
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Vaqt jadvallari",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hafta kunlari va belgilangan vaqtda avtomatik qulflash",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { showAddScheduleDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Qo'shish", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Schedules List
                if (state.schedules.isEmpty()) {
                    Text(
                        text = "Hozircha rejalashtirilgan jadvallar yo'q. Yangi jadval qo'shish uchun yuqoridagi tugmani bosing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
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
            }
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
}

/**
 * Compact, modern row displaying a scheduled focus interval with repetition chips
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        style = MaterialTheme.typography.titleSmall,
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
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (schedule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = onToggle
                    )
                }
            }

            // Bottom row: Repeat days description + Quick Actions (Hozir boshlash / O'chirish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onStartNow,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Hozir boshlash",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "O'chirish",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enhanced dialog to configure Schedule name, time range (hour:minute), and weekday recurrence
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnhancedScheduleDialog(
    onDismiss: () -> Unit,
    onAdd: (FocusSchedule) -> Unit
) {
    var title by remember { mutableStateOf("Ertalabki chuqur diqqat") }
    var startHour by remember { mutableIntStateOf(9) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(12) }
    var endMinute by remember { mutableIntStateOf(0) }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) } // 1=Mon .. 7=Sun

    val dayNames = listOf(
        1 to "Du",
        2 to "Se",
        3 to "Chor",
        4 to "Pay",
        5 to "Ju",
        6 to "Sha",
        7 to "Yak"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Vaqt jadvalini sozlash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                    text = "Vaqt oralig'i:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeBoxSelector(
                        label = "Boshlanish",
                        hour = startHour,
                        minute = startMinute,
                        onHourChange = { startHour = it },
                        onMinuteChange = { startMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text("—", fontWeight = FontWeight.Bold)
                    TimeBoxSelector(
                        label = "Tugash",
                        hour = endHour,
                        minute = endMinute,
                        onHourChange = { endHour = it },
                        onMinuteChange = { endMinute = it },
                        modifier = Modifier.weight(1f)
                    )
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
                            title = title.ifBlank { "Focus jadvali" },
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
 * Clean numeric time input box for hour and minute
 */
@Composable
private fun TimeBoxSelector(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CompactNumberField(
                    value = hour,
                    max = 23,
                    onValueChange = onHourChange
                )
                Text(":", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
                CompactNumberField(
                    value = minute,
                    max = 59,
                    onValueChange = onMinuteChange
                )
            }
        }
    }
}

@Composable
private fun CompactNumberField(
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(String.format(Locale.US, "%02d", value)) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(2)
            textValue = digits
            digits.toIntOrNull()?.let { num ->
                onValueChange(num.coerceIn(0, max))
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(48.dp),
        shape = RoundedCornerShape(8.dp)
    )
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
