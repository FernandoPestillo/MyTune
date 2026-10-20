package com.mytune.ui.components

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.mytune.presentation.*

val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun systemMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun allowed() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember(resolver) { mutableStateOf(allowed()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = allowed() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    return enabled && scale > 0f
}

@Composable
fun TuningHaptics(state: TunerUiState, enabled: Boolean, onSuccess: (() -> Unit)? = null) {
    val policy = remember { TuningFeedbackPolicy() }
    val view = LocalView.current
    val currentCallback by rememberUpdatedState(onSuccess)
    LaunchedEffect(state, enabled) {
        if (policy.accept(state, enabled, SystemClock.elapsedRealtime())) {
            val callback = currentCallback
            if (callback != null) callback() else view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
