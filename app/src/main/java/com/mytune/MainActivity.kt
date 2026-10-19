package com.mytune

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mytune.data.audio.AudioCaptureManager
import com.mytune.diagnostics.AudioPerformanceMetrics
import com.mytune.domain.audio.AudioProcessingConfig
import com.mytune.presentation.TunerViewModel
import com.mytune.ui.screens.TunerScreen
import com.mytune.ui.theme.MyTuneTheme

class MainActivity : ComponentActivity() {
    private fun hasMicrophonePermission() = ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val tuner: TunerViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val metrics = AudioPerformanceMetrics(enabled = BuildConfig.DEBUG,
                        report = { android.util.Log.d("MyTuneAudio", it) })
                    val audioConfig = AudioProcessingConfig()
                    TunerViewModel(AudioCaptureManager(applicationContext, audioConfig, metrics),
                        metrics = metrics, audioConfig = audioConfig)
                }
            })
            val state by tuner.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            var requested by rememberSaveable { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tuner.start(granted)
            }
            DisposableEffect(lifecycleOwner, tuner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> tuner.start(hasMicrophonePermission())
                        Lifecycle.Event.ON_PAUSE -> tuner.stop()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tuner.start(hasMicrophonePermission())
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer); tuner.stop() }
            }
            LaunchedEffect(Unit) {
                if (!hasMicrophonePermission() && !requested) {
                    requested = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            MyTuneTheme {
                TunerScreen(state, tuner::selectString, onMicrophoneAction = {
                    if (hasMicrophonePermission()) tuner.start(true)
                    else if (requested && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                    } else {
                        requested = true
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                })
            }
        }
    }
}
