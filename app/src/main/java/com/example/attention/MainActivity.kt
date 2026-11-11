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
    LaunchedEffect(Unit) {
        apps = usageStatsHelper.getDailyUsage()
    }

    val totalTimeMs = apps.sumOf { it.totalTimeInForeground }
    val totalHours = totalTimeMs / 1000 / 60 / 60
    val totalMinutes = (totalTimeMs / 1000 / 60) % 60
    val limitMinutes = 240 // 4 hours limit example
    val currentTotalMin = (totalTimeMs / 1000 / 60).toInt()
    val remainingMin = (limitMinutes - currentTotalMin).coerceAtLeast(0)

    val progress = (currentTotalMin.toFloat() / limitMinutes).coerceIn(0f, 1f)
    val progressColor = if (progress >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        item {
            Text("Salom, Javohir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Bugun samarali kun bo'lsin", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))
            
            // MAIN CARD - TODAY'S SCREEN TIME
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("BUGUNGI EKRAN VAQTI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${totalHours} soat ${totalMinutes} daq", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Limit: 4 soat / Qoldi: $remainingMin daq", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { }, modifier = Modifier.weight(1f)) {
                            Text("Limitni o'zgartirish", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(onClick = { }, modifier = Modifier.weight(1f)) {
                            Text("Hisobotni ko'rish", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // FOCUS SESSION CARD
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

        val maxUsage = apps.maxOfOrNull { it.totalTimeInForeground }?.toFloat() ?: 1f
        items(apps.take(3)) { app ->
            AppUsageFilledRow(app, prefs, maxUsage)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            // SLEEP MODE MINI CARD
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)), // Orange tonal equivalent
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = Color(0xFFE65100))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uyqu rejimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Text("Bugun 22:30 da avtomatik bloklanadi", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100).copy(alpha = 0.8f))
                    }
                    Switch(checked = true, onCheckedChange = { })
                }
            }
        }
    }
}

@Composable
fun AppUsageFilledRow(app: AppUsageInfo, prefs: PreferencesManager, maxUsage: Float) {
    var isBlocked by remember { mutableStateOf(prefs.isAppBlocked(app.packageName)) }
    val progress = (app.totalTimeInForeground.toFloat() / maxUsage).coerceIn(0f, 1f)
    val h = app.totalTimeInForeground / 1000 / 60 / 60
    val m = (app.totalTimeInForeground / 1000 / 60) % 60
    val timeStr = if (h > 0) "$h soat $m daq" else "$m daq"

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(app.appName.take(1), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(timeStr, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
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
}

@Composable
fun BlockingScreen(prefs: PreferencesManager) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Bloklash qoidalari", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TasksScreen(prefs: PreferencesManager) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Vazifalar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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