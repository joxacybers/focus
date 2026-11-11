package com.example.attention

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.attention.ui.theme.AttentionTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class LockOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = PreferencesManager(context)
    private var composeView: ComposeView? = null
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    fun showOverlay() {
        if (composeView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setContent {
                AttentionTheme {
                    BlockScreen(
                        prefs = prefs,
                        onGiveUp = { hideOverlay() },
                        context = context
                    )
                }
            }
        }

        overlayLifecycleOwner.start()
        windowManager.addView(composeView, params)
    }

    fun hideOverlay() {
        composeView?.let {
            windowManager.removeView(it)
            overlayLifecycleOwner.stop()
            composeView = null
        }
    }
}

@Composable
fun BlockScreen(prefs: PreferencesManager, onGiveUp: () -> Unit, context: Context) {
    val endTime = prefs.getGlobalBlockEndTime()
    val startTime = prefs.getGlobalBlockStartTime()
    val totalDuration = (endTime - startTime).coerceAtLeast(1L)

    var remainingMillis by remember { mutableStateOf(endTime - System.currentTimeMillis()) }

    // Flashlight State
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    var isFlashlightOn by remember { mutableStateOf(false) }

    // Give up State
    var giveUpClicks by remember { mutableStateOf(0) }
    var giveUpText by remember { mutableStateOf("Favqulodda chiqish") }

    LaunchedEffect(Unit) {
        while (remainingMillis > 0) {
            delay(1000)
            remainingMillis = endTime - System.currentTimeMillis()
            if (remainingMillis <= 0) {
                prefs.stopGlobalBlock()
                onGiveUp()
            }
        }
    }

    // Colors M3 strict
    val surfaceDark = Color(0xFF101410)
    val primaryGreen = Color(0xFF1B5E20)
    val primaryContainer = Color(0xFFC8E6C9)
    val onSurfaceWhite = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFF1E201E)
    val errorRed = Color(0xFFB3261E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // 1. TOP CENTER
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(48.dp).background(primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = primaryGreen)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Telefon bloklangan", style = MaterialTheme.typography.titleMedium, color = onSurfaceWhite, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Fokus rejimi faol - Chalg'ituvchi ilovalar yopiq", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }

            // 2. CENTER - WAVY PROGRESS
            val infiniteTransition = rememberInfiniteTransition(label = "wave")
            val waveScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "scale"
            )
            val waveAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha"
            )

            val progress = ((totalDuration - remainingMillis).toFloat() / totalDuration).coerceIn(0f, 1f)
            val min = (remainingMillis / 1000 / 60).coerceAtLeast(0)
            val sec = ((remainingMillis / 1000) % 60).coerceAtLeast(0)
            val timeString = String.format(Locale.getDefault(), "%02d:%02d", min, sec)

            val endFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val endString = endFormat.format(Date(endTime))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                // Wave effect
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(waveScale)
                        .alpha(waveAlpha)
                        .background(primaryGreen, CircleShape)
                )

                // M3 Circular Progress
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(240.dp),
                    color = primaryContainer,
                    trackColor = surfaceVariant,
                    strokeWidth = 12.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Timer texts
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeString, style = MaterialTheme.typography.displayLarge, color = onSurfaceWhite, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Qolgan vaqt", style = MaterialTheme.typography.bodyMedium, color = primaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Blok tugaydi: $endString da", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // 3. BELOW CENTER - CURRENT TASK
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceVariant),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hozirgi vazifa: ${prefs.getCurrentTaskName()}", style = MaterialTheme.typography.titleSmall, color = onSurfaceWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pomodoro 1 / 4", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Phone
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = surfaceVariant, contentColor = onSurfaceWhite)
                    ) {
                        Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Telefon", color = onSurfaceWhite, style = MaterialTheme.typography.labelMedium)
                }

                // Flashlight
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            try {
                                val cameraId = cameraManager.cameraIdList[0]
                                isFlashlightOn = !isFlashlightOn
                                cameraManager.setTorchMode(cameraId, isFlashlightOn)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isFlashlightOn) primaryContainer else surfaceVariant,
                            contentColor = if (isFlashlightOn) primaryGreen else onSurfaceWhite
                        )
                    ) {
                        Icon(Icons.Outlined.FlashlightOn, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fonar", color = onSurfaceWhite, style = MaterialTheme.typography.labelMedium)
                }

                // SMS
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_MESSAGING)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                context.startActivity(fallback)
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = surfaceVariant, contentColor = onSurfaceWhite)
                    ) {
                        Icon(Icons.Outlined.Message, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("SMS", color = onSurfaceWhite, style = MaterialTheme.typography.labelMedium)
                }
            }

            // 5. VERY BOTTOM
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Faqat Telefon va SMS ga ruxsat berilgan", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (giveUpClicks == 1) {
                            prefs.stopGlobalBlock()
                            onGiveUp()
                        } else {
                            giveUpClicks++
                            giveUpText = "Tasdiqlash uchun yana bosing"
                        }
                    },
                    border = BorderStroke(1.dp, surfaceVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = errorRed)
                ) {
                    Text(giveUpText)
                }
            }
        }
    }
}

// Required for ComposeView in WindowManager
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun start() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}