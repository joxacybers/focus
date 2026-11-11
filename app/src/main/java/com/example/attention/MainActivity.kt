package com.example.attention

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.attention.ui.theme.AttentionTheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Default.HourglassEmpty),
    Blocking("Blocking", Icons.Default.Security),
    Tasks("Tasks", Icons.Default.CheckCircle),
    Settings("Stats", Icons.Default.BarChart)
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: PreferencesManager
    private lateinit var usageStatsHelper: UsageStatsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        usageStatsHelper = UsageStatsHelper(this)
        enableEdgeToEdge()

        setContent {
            AttentionTheme {
                AppNavigation(prefs, usageStatsHelper)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(prefs: PreferencesManager, usageStatsHelper: UsageStatsHelper) {
    var currentScreen by remember { mutableStateOf(Screen.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentScreen.title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelMedium) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentScreen) {
                Screen.Home -> HomeScreen(prefs, usageStatsHelper)
                Screen.Blocking -> BlockingScreen(prefs)
                Screen.Tasks -> TasksScreen(prefs)
                Screen.Settings -> SettingsScreen(prefs)
            }
        }
    }
}

@Composable
fun HomeScreen(prefs: PreferencesManager, usageStatsHelper: UsageStatsHelper) {
    var apps by remember { mutableStateOf(emptyList<AppUsageInfo>()) }
    var weeklyStats by remember { mutableStateOf(emptyList<DailyStats>()) }
    
    LaunchedEffect(Unit) {
        apps = usageStatsHelper.getDailyUsage()
        weeklyStats = usageStatsHelper.getWeeklyUsage()
    }

    val todayScreenTime = weeklyStats.lastOrNull()?.totalTimeMs ?: 0L
    val totalMinutes = todayScreenTime / 1000 / 60
    val limitMinutes = 240 // 4 hours limit example
    val progress = (totalMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)

    val progressColor = when {
        progress >= 1f -> MaterialTheme.colorScheme.error
        progress >= 0.8f -> MaterialTheme.colorScheme.tertiary // Orange-ish in some themes
        else -> MaterialTheme.colorScheme.primary
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Bugungi jami vaqt", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${totalMinutes / 60}s ${totalMinutes % 60}d / 4s limit", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            // Weekly Rhythm Chart
            if (weeklyStats.isNotEmpty()) {
                WeeklyRhythmCard(weeklyStats)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Eng ko'p chalg'itadigan ilovalar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        val maxUsage = apps.maxOfOrNull { it.totalTimeInForeground }?.toFloat() ?: 1f
        items(apps.take(10)) { app ->
            AppUsageM3Row(app, prefs, maxUsage)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun WeeklyRhythmCard(weeklyStats: List<DailyStats>) {
    val totalWeeklyTime = weeklyStats.sumOf { it.totalTimeMs }
    val avgTimeMs = if (weeklyStats.isNotEmpty()) totalWeeklyTime / weeklyStats.size else 0
    val avgHours = (avgTimeMs / 1000 / 60) / 60
    val avgMinutes = (avgTimeMs / 1000 / 60) % 60
    
    val maxTime = weeklyStats.maxOfOrNull { it.totalTimeMs }?.coerceAtLeast(1L) ?: 1L

    var selectedIndex by remember { mutableStateOf(weeklyStats.size - 1) }
    val selectedStat = weeklyStats.getOrNull(selectedIndex)
    val selHours = (selectedStat?.totalTimeMs ?: 0L) / 1000 / 60 / 60
    val selMinutes = ((selectedStat?.totalTimeMs ?: 0L) / 1000 / 60) % 60
    val selDay = selectedStat?.dayName ?: ""

    FilledCard(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("$selDay: ${selHours}s ${selMinutes}d", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("O'rtacha kunlik: ${avgHours}s ${avgMinutes}d", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyStats.forEachIndexed { index, stat ->
                    val isSelected = index == selectedIndex
                    val isToday = index == weeklyStats.size - 1
                    val barHeightFraction = (stat.totalTimeMs.toFloat() / maxTime.toFloat()).coerceIn(0.1f, 1f)
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight().clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            selectedIndex = index
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .fillMaxHeight(barHeightFraction)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stat.dayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageM3Row(app: AppUsageInfo, prefs: PreferencesManager, maxUsage: Float) {
    var isBlocked by remember { mutableStateOf(prefs.isAppBlocked(app.packageName)) }
    val progress = (app.totalTimeInForeground.toFloat() / maxUsage).coerceIn(0f, 1f)
    val minutes = app.totalTimeInForeground / 1000 / 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("${minutes} daq", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isBlocked,
            onCheckedChange = { checked ->
                isBlocked = checked
                prefs.setAppBlocked(app.packageName, checked)
            },
            modifier = Modifier.scale(0.8f) // Making the switch slightly smaller/compact
        )
    }
}

@Composable
fun BlockingScreen(prefs: PreferencesManager) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scheduled Blocks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        FilledCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Sleep Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("22:30 - 07:00", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Allowed: Phone, SMS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Switch(checked = false, onCheckedChange = { }) // Placeholder for UI
            }
        }
    }
}

@Composable
fun TasksScreen(prefs: PreferencesManager) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Focus Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Dars qilish", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("4 Pomodoro (25m work / 5m break)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(
                    onClick = {
                        // Start 25 min Pomodoro Block
                        prefs.startGlobalBlock(25 * 60_000L, "Dars qilish")
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Start Task", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(prefs: PreferencesManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("System Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Required for strict blocking", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("1. Usage Access") }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("2. Overlay Permission") }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("3. Accessibility") }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = {
            val componentName = ComponentName(context, AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to prevent uninstallation.")
            }
            context.startActivity(intent)
        }) { Text("4. Device Administrator") }
        
        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Ilova yangilanishi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("GitHub'dan so'nggi versiyani tekshirish", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = {
                if (!isChecking) {
                    isChecking = true
                    coroutineScope.launch {
                        val updater = UpdateManager(context)
                        val hasUpdate = updater.checkForUpdates()
                        if (!hasUpdate) {
                            Toast.makeText(context, "Siz eng so'nggi versiyadasiz", Toast.LENGTH_SHORT).show()
                        }
                        isChecking = false
                    }
                }
            },
            enabled = !isChecking
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text("Yangilanishni tekshirish")
        }
    }
}

@Composable
fun FilledCard(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape, colors: CardColors, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = shape, colors = colors, content = content)
}