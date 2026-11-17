package com.example.focus.ui.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.focus.data.FocusRepository
import com.example.focus.data.FocusState
import com.example.focus.data.PomodoroPhase
import com.example.focus.data.SessionMode
import com.example.focus.ui.theme.FocusTheme
import com.example.focus.ui.theme.PlusJakartaSans
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusRepository.init(this)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Cover the entire screen including status bar, navigation bar, and camera cutout
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (FocusRepository.state.value.keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        hideSystemBarsImmersive()

        // Block back button from exiting the lock screen
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    hideSystemBarsImmersive()
                }
            }
        )

        setContent {
            val state by FocusRepository.state.collectAsState()

            // Automatically close LockScreenActivity when session finishes
            LaunchedEffect(state.isLocked) {
                if (!state.isLocked) {
                    if (FocusRepository.isInLockTaskMode(this@LockScreenActivity)) {
                        try {
                            stopLockTask()
                        } catch (_: Exception) {
                        }
                    }
                    finish()
                }
            }

            // Optionally activate Screen Pinning (Lock Task Mode) if enabled in settings
            LaunchedEffect(state.useScreenPinning, state.isLocked) {
                if (state.isLocked && state.useScreenPinning && !FocusRepository.isInLockTaskMode(this@LockScreenActivity)) {
                    try {
                        startLockTask()
                    } catch (_: Exception) {
                    }
                }
            }

            FocusTheme(
                darkTheme = true, // Lock screen uses deep immersive dark surface for battery saving & focus
                dynamicColor = state.dynamicColors
            ) {
                FullLockScreenContent(
                    state = state,
                    onOpenPhone = {
                        FocusRepository.launchDialerApp(this@LockScreenActivity)
                    },
                    onToggleFlashlight = {
                        FocusRepository.toggleTorch()
                    },
                    onOpenMessages = {
                        FocusRepository.launchMessagesApp(this@LockScreenActivity)
                    },
                    onToggleScreenPinning = {
                        try {
                            if (FocusRepository.isInLockTaskMode(this@LockScreenActivity)) {
                                stopLockTask()
                            } else {
                                startLockTask()
                            }
                        } catch (_: Exception) {
                        }
                    },
                    onEmergencyStop = {
                        if (FocusRepository.isInLockTaskMode(this@LockScreenActivity)) {
                            try {
                                stopLockTask()
                            } catch (_: Exception) {
                            }
                        }
                        FocusRepository.stopSession(this@LockScreenActivity, completed = false)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FocusRepository.grantTemporaryAllowedPackage(null)
        hideSystemBarsImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsImmersive()
        } else {
            // Collapse system shade if user tries to pull down Control Panel
            val state = FocusRepository.state.value
            if (state.isLocked && state.blockControlPanel && state.activeAllowedPackage == null) {
                window.decorView.postDelayed({
                    hideSystemBarsImmersive()
                }, 100L)
            }
        }
    }

    private fun hideSystemBarsImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun FullLockScreenContent(
    state: FocusState,
    onOpenPhone: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onOpenMessages: () -> Unit,
    onToggleScreenPinning: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val context = LocalContext.current

    // Real-time clock & date
    var currentTimeText by remember { mutableStateOf("") }
    var currentDateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d-MMMM", Locale.forLanguageTag("uz"))
        while (true) {
            val now = Date()
            currentTimeText = timeFormat.format(now)
            currentDateText = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            delay(1_000L)
        }
    }

    // Real-time Battery state (Zaryad belgisi va foizi)
    var batteryPct by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100) / scale
                }
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
        }
    }

    var showEmergencyDialog by remember { mutableStateOf(false) }

    val progress = remember(state.remainingSeconds, state.totalDurationSeconds) {
        if (state.totalDurationSeconds <= 0L) 0f
        else (state.remainingSeconds.toFloat() / state.totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "timer_progress"
    )

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0A0A0E),
            Color(0xFF14141A),
            Color(0xFF0A0A0E)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Floating Top-Right Battery Badge (Suzuvchi batareya indikatori)
        FloatingBatteryBadge(
            batteryPct = batteryPct,
            isCharging = isCharging,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. LARGE CLOCK & MODE STATUS HEADER
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTimeText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = PlusJakartaSans,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = Color.White
                )
                Text(
                    text = currentDateText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.72f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Grayscale Active Mode Pill
                val badgeColor = Color(0xFF383838)

                Surface(
                    color = badgeColor.copy(alpha = 0.4f),
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, Color(0xFF666666), CircleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "FOCUSLOCK REJIMI • ${state.sessionTitle}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // 2. CIRCULAR COUNTDOWN RING (Google Material 3 Expressive)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(260.dp)
            ) {
                GoogleExpressiveCountdownProgress(
                    progress = animatedProgress
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "QOLGAN VAQT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatDuration(state.remainingSeconds),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 46.sp,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Status chips (Grayscale)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "To'liq himoya faol",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PlusJakartaSans
                            ),
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // 3. ONLY ALLOWED APPS / TOOLS (Telefon, Fonor, Xabarlar)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "RUXSAT ETILGAN VOSITALAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 1.4.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AllowedQuickActionButton(
                        icon = Icons.Default.Call,
                        label = "Telefon",
                        isActive = false,
                        accentColor = Color(0xFFD4D4D4),
                        onClick = onOpenPhone
                    )

                    AllowedQuickActionButton(
                        icon = if (state.isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        label = if (state.isTorchOn) "Fonor (Yoqiq)" else "Fonor",
                        isActive = state.isTorchOn,
                        accentColor = Color(0xFFEFEFEF),
                        onClick = onToggleFlashlight
                    )

                    AllowedQuickActionButton(
                        icon = Icons.AutoMirrored.Filled.Message,
                        label = "Xabarlar",
                        isActive = false,
                        accentColor = Color(0xFFD4D4D4),
                        onClick = onOpenMessages
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom row: Screen Pinning helper & Emergency Unlock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onToggleScreenPinning
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ekranni mixlash",
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    TextButton(
                        onClick = { showEmergencyDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.strictMode) "Favqulodda chiqish" else "Yakunlash",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    if (showEmergencyDialog) {
        EmergencyUnlockDialog(
            strictMode = state.strictMode,
            onDismiss = { showEmergencyDialog = false },
            onConfirmUnlock = {
                showEmergencyDialog = false
                onEmergencyStop()
            }
        )
    }
}

/**
 * Google Material 3 Expressive Circular Progress Countdown Ring
 * Incorporates rounded caps, signature track gap, and subtle ambient wavy orbit from M3 Expressive design
 */
@Composable
private fun GoogleExpressiveCountdownProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 14.dp.toPx()
        val diameter = size.minDimension - strokeWidth - 18.dp.toPx()
        val radius = diameter / 2f
        val centerOffset = Offset(size.width / 2f, size.height / 2f)
        val arcTopLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
        val arcSize = Size(diameter, diameter)

        // 1. Outer Google M3 Expressive Decorative Wavy Orbit Ring
        val wavyRadius = radius + 11.dp.toPx()
        val waveAmplitude = 2.4.dp.toPx()
        val waveCount = 20
        val wavyPath = Path()
        val step = 2.0
        var angle = 0.0
        while (angle <= 360.0) {
            val rad = Math.toRadians(angle)
            val currentR = wavyRadius + waveAmplitude * Math.sin(waveCount * rad).toFloat()
            val x = centerOffset.x + (currentR * Math.cos(rad)).toFloat()
            val y = centerOffset.y + (currentR * Math.sin(rad)).toFloat()
            if (angle == 0.0) {
                wavyPath.moveTo(x, y)
            } else {
                wavyPath.lineTo(x, y)
            }
            angle += step
        }
        wavyPath.close()
        drawPath(
            path = wavyPath,
            color = Color.White.copy(alpha = 0.08f),
            style = Stroke(width = 1.4.dp.toPx())
        )

        // 2. Google M3 Track: Semi-translucent with rounded stroke
        drawArc(
            color = Color.White.copy(alpha = 0.12f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 3. Google M3 Expressive Active Indicator with rounded caps & smooth highlight
        if (progress > 0.005f) {
            val sweep = (360f * progress).coerceIn(1f, 360f)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFE2E2E8),
                        Color(0xFFFFFFFF)
                    ),
                    center = centerOffset
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Distinctive Google M3 accent tip circle at active cap
            val endAngleRad = Math.toRadians((-90.0 + sweep).toDouble())
            val tipX = centerOffset.x + (radius * Math.cos(endAngleRad)).toFloat()
            val tipY = centerOffset.y + (radius * Math.sin(endAngleRad)).toFloat()
            drawCircle(
                color = Color.White,
                radius = strokeWidth * 0.44f,
                center = Offset(tipX, tipY)
            )
        }
    }
}

/**
 * Floating glass battery badge in top right corner
 */
@Composable
private fun FloatingBatteryBadge(
    batteryPct: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val batteryColor = when {
        isCharging -> Color(0xFF69F0AE)
        batteryPct <= 15 -> Color(0xFFFF5252)
        else -> Color.White
    }

    Surface(
        color = Color.White.copy(alpha = 0.09f),
        shape = CircleShape,
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = when {
                    isCharging -> Icons.Default.BatteryChargingFull
                    batteryPct >= 80 -> Icons.Default.BatteryFull
                    else -> Icons.Default.BatteryStd
                },
                contentDescription = "Zaryad",
                tint = batteryColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$batteryPct%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold
                ),
                color = batteryColor
            )
        }
    }
}

@Composable
private fun AllowedQuickActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) accentColor else Color.White.copy(alpha = 0.1f),
        label = "quick_action_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF101726) else Color.White,
        label = "quick_action_content"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "torch_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .scale(if (isActive) pulseScale else 1f)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = 1.5.dp,
                    color = if (isActive) accentColor else accentColor.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) accentColor else Color.White.copy(alpha = 0.9f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun EmergencyUnlockDialog(
    strictMode: Boolean,
    onDismiss: () -> Unit,
    onConfirmUnlock: () -> Unit
) {
    val factorA = remember { Random.nextInt(12, 29) }
    val factorB = remember { Random.nextInt(11, 19) }
    val addendC = remember { Random.nextInt(15, 75) }
    val expectedAnswer = remember(factorA, factorB, addendC) { factorA * factorB + addendC }
    var userInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (strictMode) "Qat'iy Focus Rejimi Faol!" else "Sessiyani yakunlash",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (strictMode) {
                    Text(
                        text = "Siz Qat'iy Rejimni yoqqansiz. Chalg'imaslik uchun vaqt tugashini kuting. Agar favqulodda holat bo'lsa, quyidagi misolni yechib qulfni oching:"
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "$factorA × $factorB + $addendC = ?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = {
                            userInput = it
                            errorText = null
                        },
                        label = { Text("Javobni kiriting") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = errorText != null,
                        supportingText = errorText?.let { msg -> { Text(msg) } }
                    )
                } else {
                    Text("Haqiqatan ham joriy Focus/Study sessiyasini muddatidan oldin to'xtatmoqchimisiz?")
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                onClick = {
                    if (strictMode) {
                        if (userInput.trim().toIntOrNull() == expectedAnswer) {
                            onConfirmUnlock()
                        } else {
                            errorText = "Javob noto'g'ri! Diqqatingizni jamlang."
                        }
                    } else {
                        onConfirmUnlock()
                    }
                }
            ) {
                Text("Qulfni ochish")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Davom ettirish")
            }
        }
    )
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
