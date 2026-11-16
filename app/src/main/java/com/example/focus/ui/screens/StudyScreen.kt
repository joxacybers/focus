package com.example.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focus.data.FocusRepository
import com.example.focus.data.FocusState
import com.example.focus.data.SessionMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyScreen(
    state: FocusState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPomodoroTab by remember { mutableStateOf(true) }
    var studyTopic by remember { mutableStateOf("Dars va kitob mutolaasi") }

    // Custom timer state (default 3 minutes "3d" or 45 minutes)
    var customStudyMinutes by remember { mutableIntStateOf(3) }

    // Pomodoro state (4 or 5 rounds as specifically requested!)
    var pomodoroRounds by remember { mutableIntStateOf(4) }
    var pomodoroWorkMinutes by remember { mutableIntStateOf(25) }
    var pomodoroBreakMinutes by remember { mutableIntStateOf(5) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Study Mode Header & Stats Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Study Bo'limi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mutlaq sukunat: bildirishnoma, ovoz va barcha chalg'ituvchi ilovalar o'chiriladi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${state.totalFocusedMinutes} daq",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.completedSessionsCount} sessiya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Mode Switcher: Pomodoro Method vs Custom Study Timer
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isPomodoroTab,
                onClick = { isPomodoroTab = true },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text("Pomodoro (4-5 bosqich)")
            }
            SegmentedButton(
                selected = !isPomodoroTab,
                onClick = { isPomodoroTab = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text("Aniq vaqt (3d / 45d)")
            }
        }

        if (isPomodoroTab) {
            // POMODORO STUDY CARD
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Pomodoro O'qish Metodikasi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = studyTopic,
                        onValueChange = { studyTopic = it },
                        label = { Text("O'qish mavzusi / Fan nomi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 1. Number of Pomodoro Rounds (specifically highlighting 4 and 5!)
                    Text(
                        text = "Bosqichlar (Sikllar) sonini tanlang:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val roundOptions = listOf(2, 3, 4, 5, 6, 8)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roundOptions.forEach { count ->
                            FilterChip(
                                selected = pomodoroRounds == count,
                                onClick = { pomodoroRounds = count },
                                label = {
                                    Text(
                                        text = "$count ta bosqich",
                                        fontWeight = if (count == 4 || count == 5) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    // 2. Work Duration per Round
                    Text(
                        text = "Har bir o'qish bosqichi davomiyligi:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val workOptions = listOf(
                        3 to "3 daqiqa (3d sinov)",
                        15 to "15 daqiqa",
                        25 to "25 daqiqa (Klassik)",
                        45 to "45 daqiqa",
                        50 to "50 daqiqa"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        workOptions.forEach { (mins, label) ->
                            FilterChip(
                                selected = pomodoroWorkMinutes == mins,
                                onClick = { pomodoroWorkMinutes = mins },
                                label = { Text(label) }
                            )
                        }
                    }

                    // 3. Break Duration
                    Text(
                        text = "Qisqa tanaffus vaqti:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val breakOptions = listOf(
                        1 to "1 daq",
                        5 to "5 daq (Standart)",
                        10 to "10 daq",
                        15 to "15 daq"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        breakOptions.forEach { (mins, label) ->
                            FilterChip(
                                selected = pomodoroBreakMinutes == mins,
                                onClick = { pomodoroBreakMinutes = mins },
                                label = { Text(label) }
                            )
                        }
                    }

                    // Summary calculation card
                    val totalFocusMins = pomodoroRounds * pomodoroWorkMinutes
                    val totalBreakMins = (pomodoroRounds - 1).coerceAtLeast(0) * pomodoroBreakMinutes
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Reja: $pomodoroRounds bosqich × $pomodoroWorkMinutes daqiqa o'qish",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Jami sof o'qish vaqti: $totalFocusMins daqiqa • Tanaffuslar: $totalBreakMins daqiqa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    Button(
                        onClick = {
                            FocusRepository.startPomodoroSession(
                                context = context,
                                rounds = pomodoroRounds,
                                workMinutes = pomodoroWorkMinutes,
                                breakMinutes = pomodoroBreakMinutes,
                                title = studyTopic.ifBlank { "Pomodoro O'qish" }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pomodoro o'qishni boshlash ($pomodoroRounds bosqich)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // CUSTOM TIME STUDY CARD (3d, 25d, 45d, 60d, etc.)
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Aniq vaqtga o'qish rejimi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = studyTopic,
                        onValueChange = { studyTopic = it },
                        label = { Text("O'qish mavzusi / Vazifa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$customStudyMinutes daqiqa (${customStudyMinutes}d)",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = customStudyMinutes.toFloat(),
                                onValueChange = { customStudyMinutes = it.roundToInt().coerceAtLeast(1) },
                                valueRange = 1f..180f
                            )
                        }
                    }

                    Text(
                        text = "Tezkor o'qish vaqtlari:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    val studyPresets = listOf(
                        3 to "3d (3 daqiqa)",
                        5 to "5d",
                        15 to "15d",
                        25 to "25d",
                        45 to "45d (1 para)",
                        60 to "60d (1 soat)",
                        90 to "90d (Juftlik)",
                        120 to "120d (2 soat)"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        studyPresets.forEach { (mins, label) ->
                            FilterChip(
                                selected = customStudyMinutes == mins,
                                onClick = { customStudyMinutes = mins },
                                label = { Text(label) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            FocusRepository.startFocusSession(
                                context = context,
                                durationMinutes = customStudyMinutes,
                                title = studyTopic.ifBlank { "Study ($customStudyMinutes daq)" },
                                mode = SessionMode.STUDY_TIMER
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "O'qishni boshlash (${customStudyMinutes}d)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Strict Study Protection Info Card
        OutlinedCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Study vaqtida avtomatik cheklovlar:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Barcha bildirishnomalar (Notifications) bloklanadi va o'chiriladi",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Telefon ovozi va chalg'ituvchi tovushlar Sukut (Silent/DND) rejimiga o'tadi",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.DoNotDisturbOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Control Panel va boshqa barcha ilovalar yopiladi (faqat Telefon, Fonor va Xabarlar ochiq)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Zaryad foizi, antenna va soat bevosita blok ekranining o'zida ko'rsatiladi",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
