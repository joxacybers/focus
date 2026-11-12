package com.example.attention

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.attention.ui.theme.AttentionTheme
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Bosh sahifa", Icons.Outlined.Home, Icons.Filled.Home),
    Blocking("Bloklash", Icons.Outlined.Security, Icons.Filled.Security),
    Tasks("Vazifalar", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    Settings("Statistika", Icons.Outlined.BarChart, Icons.Filled.BarChart)
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
            TopAppBar(
                title = { Text("Focus", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { }) {
                        BadgedBox(badge = { Badge(modifier = Modifier.size(8.dp)) }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                        }
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Screen.entries.forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        icon = { Icon(if (selected) screen.selectedIcon else screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelMedium) },
                        selected = selected,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentScreen) {
                Screen.Home -> HomeScreen(prefs, usageStatsHelper)
                Screen.Blocking -> BlockingScreen(prefs, usageStatsHelper)
                Screen.Tasks -> TasksScreen(prefs)
                Screen.Settings -> SettingsScreen(prefs)
            }
        }
    }
}

@Composable
fun HomeScreen(prefs: PreferencesManager, usageStatsHelper: UsageStatsHelper) {
    var weeklyStats by remember { mutableStateOf(emptyList<DailyStats>()) }
    var selectedDayIndex by remember { mutableStateOf(0) }
    var selectedDayApps by remember { mutableStateOf(emptyList<AppUsageInfo>()) }

    LaunchedEffect(Unit) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val stats = usageStatsHelper.getWeeklyScreenTime()
            val cal = Calendar.getInstance()
            val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-based for Sun-Sat
            val todayStats = stats.getOrNull(currentDayOfWeek)
            val todayApps = if (todayStats != null) {
                usageStatsHelper.getDailyUsage(todayStats.startTime, todayStats.endTime)
            } else emptyList()
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                weeklyStats = stats
                selectedDayIndex = currentDayOfWeek
                selectedDayApps = todayApps
            }
        }
    }

    if (weeklyStats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val selectedStat = weeklyStats[selectedDayIndex]
    val totalTimeMs = selectedStat.totalTimeMs
    val totalHours = totalTimeMs / 1000 / 60 / 60
    val totalMinutes = (totalTimeMs / 1000 / 60) % 60
    val limitMinutes = 240 // 4 hours limit example
    val currentTotalMin = (totalTimeMs / 1000 / 60).toInt()
    val remainingMin = (limitMinutes - currentTotalMin).coerceAtLeast(0)

    val progress = (currentTotalMin.toFloat() / limitMinutes).coerceIn(0f, 1f)
    val progressColor = if (progress >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    // Formatting date (e.g. "Wed, Nov 11")
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.ENGLISH)
    val dateString = dateFormat.format(Date(selectedStat.startTime))
    val isToday = selectedDayIndex == (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1)
    val dayLabel = if (isToday) "Today" else dateString

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item {
            Text("Salom, Javohir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Bugun samarali kun bo'lsin", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))
            
            // SELECTED DAY SCREEN TIME
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${totalHours} soat ${totalMinutes} daq",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // WEEKLY CHART CARD
            WeeklyChartCard(
                weeklyStats = weeklyStats,
                selectedIndex = selectedDayIndex,
                onDaySelected = { index ->
                    selectedDayIndex = index
                    val stat = weeklyStats[index]
                    selectedDayApps = usageStatsHelper.getDailyUsage(stat.startTime, stat.endTime)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // REMOVED: Focus Session Card is now in TasksScreen
            
            Spacer(modifier = Modifier.height(24.dp))

            // MOST USED APPS
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Eng ko'p ishlatilgan ilovalar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { }) {
                    Text("Barchasi")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val maxUsage = selectedDayApps.maxOfOrNull { it.totalTimeInForeground }?.toFloat() ?: 1f
        
        items(selectedDayApps.take(3)) { app ->
            val pm = LocalContext.current.packageManager
            AppUsageFilledRow(app, prefs, maxUsage, pm)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            // SLEEP MODE MINI CARD
            val isSleepModeEnabled = prefs.isSleepModeEnabled()
            val startTimeStr = String.format(Locale.getDefault(), "%02d:%02d", prefs.getSleepStartHour(), prefs.getSleepStartMinute())
            
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)), // Orange tonal equivalent
                modifier = Modifier.fillMaxWidth().clickable {
                    // Kelajakda TimePicker ochish mumkin
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = Color(0xFFE65100))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uyqu rejimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Text("Bugun $startTimeStr da avtomatik bloklanadi", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100).copy(alpha = 0.8f))
                    }
                    var localSleepMode by remember { mutableStateOf(isSleepModeEnabled) }
                    Switch(
                        checked = localSleepMode, 
                        onCheckedChange = { checked ->
                            localSleepMode = checked
                            prefs.setSleepModeEnabled(checked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyChartCard(weeklyStats: List<DailyStats>, selectedIndex: Int, onDaySelected: (Int) -> Unit) {
    val maxTimeMs = weeklyStats.maxOfOrNull { it.totalTimeMs }?.coerceAtLeast(1L) ?: 1L
    val maxTimeHoursFloat = maxTimeMs / 1000f / 60f / 60f
    
    // Choose an upper bound for the graph based on the maximum usage
    val graphTopHour = when {
        maxTimeHoursFloat <= 2f -> 2
        maxTimeHoursFloat <= 4f -> 4
        maxTimeHoursFloat <= 6f -> 6
        maxTimeHoursFloat <= 8f -> 8
        maxTimeHoursFloat <= 12f -> 12
        else -> 24
    }
    
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp)) {
            // Horizontal lines and labels (Y-axis)
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                // Usually 3 or 4 segments depending on height. Let's do 2 segments for cleanliness
                val lines = 2
                val step = graphTopHour / lines // e.g. if top is 8, steps are 4, 8.
                for (i in lines downTo 0) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i * step}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    }
                    if (i > 0) Spacer(modifier = Modifier.weight(1f))
                }
            }
            
            // Bars (X-axis)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp).fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyStats.forEachIndexed { index, stat ->
                    val isSelected = index == selectedIndex
                    val heightFraction = (stat.totalTimeMs.toFloat() / (graphTopHour * 60 * 60 * 1000f)).coerceIn(0f, 1f)
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight().clickable { onDaySelected(index) }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .fillMaxHeight(heightFraction)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stat.dayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageFilledRow(app: AppUsageInfo, prefs: PreferencesManager, maxUsage: Float, pm: android.content.pm.PackageManager, showTime: Boolean = true) {
    var isBlocked by remember { mutableStateOf(prefs.isAppBlocked(app.packageName)) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = remember(app.packageName) {
            try {
                pm.getApplicationIcon(app.packageName)
            } catch (e: Exception) {
                null
            }
        }
        
        if (icon != null) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        setImageDrawable(icon)
                    }
                },
                modifier = Modifier.size(40.dp)
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(app.appName.take(1), fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            if (showTime) {
                val progress = (app.totalTimeInForeground.toFloat() / maxUsage).coerceIn(0f, 1f)
                val h = app.totalTimeInForeground / 1000 / 60 / 60
                val m = (app.totalTimeInForeground / 1000 / 60) % 60
                val timeStr = if (h > 0) "$h soat $m daq" else "$m daq"
                
                Text(timeStr, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = isBlocked,
            onCheckedChange = { checked ->
                isBlocked = checked
                prefs.setAppBlocked(app.packageName, checked)
            }
        )
    }
}

@Composable
fun BlockingScreen(prefs: PreferencesManager, usageStatsHelper: UsageStatsHelper) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Ekran (Intervalli bloklash)", "Ilovalarni bloklash")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (selectedTabIndex) {
                0 -> ScreenBlockingTab(prefs)
                1 -> AppBlockingTab(prefs, usageStatsHelper)
            }
        }
    }
}

@Composable
fun ScreenBlockingTab(prefs: PreferencesManager) {
    val context = LocalContext.current
    val isSleepModeEnabled = prefs.isSleepModeEnabled()
    val startTimeStr = String.format(Locale.getDefault(), "%02d:%02d", prefs.getSleepStartHour(), prefs.getSleepStartMinute())
    var showSleepSettings by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Rejali bloklashlar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)),
            modifier = Modifier.fillMaxWidth().clickable { showSleepSettings = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = Color(0xFFE65100))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Uyqu rejimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Text("Har kuni $startTimeStr da qulflanadi", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100).copy(alpha = 0.8f))
                }
                var localSleepMode by remember { mutableStateOf(isSleepModeEnabled) }
                Switch(
                    checked = localSleepMode, 
                    onCheckedChange = { checked ->
                        localSleepMode = checked
                        prefs.setSleepModeEnabled(checked)
                    }
                )
            }
        }

        if (showSleepSettings) {
            SleepSettingsBottomSheet(
                prefs = prefs,
                onDismiss = { showSleepSettings = false },
                context = context
            )
        }
    }
}

@Composable
fun AppBlockingTab(prefs: PreferencesManager, usageStatsHelper: UsageStatsHelper) {
    var installedApps by remember { mutableStateOf(emptyList<AppUsageInfo>()) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apps = usageStatsHelper.getAllInstalledApps()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                installedApps = apps
            }
        }
    }

    if (installedApps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pm = LocalContext.current.packageManager
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(installedApps) { app ->
            AppUsageFilledRow(app = app, prefs = prefs, maxUsage = 1f, pm = pm, showTime = false)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TasksScreen(prefs: PreferencesManager) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Vazifalar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        // FOCUS SESSION CARD (Moved from Home)
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fokus sessiyasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Oxirgi: Dars qilish - 50 daq", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    prefs.startGlobalBlock(25 * 60_000L, "Fokus sessiyasi")
                }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Boshlash")
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
        Text("Ruxsatnomalar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Usage Access") }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("Overlay Permission") }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = {
            val componentName = ComponentName(context, AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to prevent uninstallation.")
            }
            context.startActivity(intent)
        }) { Text("Device Administrator") }
        
        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Ilova yangilanishi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingsBottomSheet(
    prefs: PreferencesManager,
    onDismiss: () -> Unit,
    context: android.content.Context
) {
    var startH by remember { mutableStateOf(prefs.getSleepStartHour()) }
    var startM by remember { mutableStateOf(prefs.getSleepStartMinute()) }
    var endH by remember { mutableStateOf(prefs.getSleepEndHour()) }
    var endM by remember { mutableStateOf(prefs.getSleepEndMinute()) }

    val startStr = String.format(Locale.getDefault(), "%02d:%02d", startH, startM)
    val endStr = String.format(Locale.getDefault(), "%02d:%02d", endH, endM)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Uyqu rejimini sozlash", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Boshlanish", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m ->
                            startH = h
                            startM = m
                            prefs.setSleepStart(h, m)
                        }, startH, startM, true).show()
                    }) {
                        Text(startStr, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tugash", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m ->
                            endH = h
                            endM = m
                            prefs.setSleepEnd(h, m)
                        }, endH, endM, true).show()
                    }) {
                        Text(endStr, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            FilledTonalButton(
                onClick = { LockOverlayManager(context).showPreview(true) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Outlined.Visibility, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Qulf ekranini ko'rish (Preview)")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
