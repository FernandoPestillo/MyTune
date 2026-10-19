package com.mytune.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.media.audiofx.*
import com.mytune.domain.audio.AudioProcessingConfig
import com.mytune.diagnostics.AudioPerformanceMetrics
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/** Owns exactly one recorder per call. Never writes samples to disk. */
class AudioCaptureManager(
    context: Context,
    private val config: AudioProcessingConfig = AudioProcessingConfig(),
    private val metrics: AudioPerformanceMetrics = AudioPerformanceMetrics(),
) : AudioCaptureSource {
    private class Window(size: Int) {
        val samples = FloatArray(size)
        var availableNs = 0L
    }
    private val context = context.applicationContext

    override suspend fun capture(
        onStarted: (Int) -> Unit,
        onSamples: (FloatArray, Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) throw SecurityException("Permissão de microfone necessária")
        val recorder = openRecorder()
        val effects = mutableListOf<AudioEffect>()
        try {
            // Sources already avoid voice processing. Also disable controllable session effects;
            // unavailable/locked vendor effects are diagnostic limitations, never startup failures.
            fun disable(create: () -> AudioEffect?) {
                try {
                    create()?.let { effect ->
                        effects += effect
                        if (effect.hasControl()) effect.enabled = false
                    }
                } catch (_: RuntimeException) { /* Unsupported by this device/session. */ }
            }
            if (AcousticEchoCanceler.isAvailable()) disable { AcousticEchoCanceler.create(recorder.audioSessionId) }
            if (AutomaticGainControl.isAvailable()) disable { AutomaticGainControl.create(recorder.audioSessionId) }
            if (NoiseSuppressor.isAvailable()) disable { NoiseSuppressor.create(recorder.audioSessionId) }
            coroutineContext.ensureActive()
            val sampleRate = recorder.sampleRate
            if (metrics.enabled) metrics.captureConfiguration(recorder.audioSource, sampleRate, recorder.bufferSizeInFrames,
                config.windowSize, config.hopSize, effects.joinToString(";") { "${it.javaClass.simpleName}:${it.enabled}" })
            onStarted(sampleRate)
            coroutineScope {
                val pcm = ShortArray(config.hopSize)
                val ring = AudioBufferManager(config)
                val free = Channel<Window>(3)
                repeat(3) { free.trySend(Window(config.windowSize)).getOrThrow() }
                val pending = Channel<Window>(1, BufferOverflow.DROP_OLDEST, onUndeliveredElement = {
                    free.trySend(it)
                    metrics.droppedWindow()
                })
                // One CPU worker; capture keeps draining AudioRecord while analysis is running.
                val worker = launch(Dispatchers.Default) {
                    for (window in pending) {
                        val started = metrics.nowNs()
                        try { onSamples(window.samples, sampleRate) }
                        finally {
                            metrics.window(window.availableNs, started, metrics.nowNs())
                            free.trySend(window)
                        }
                    }
                }
                var checks = 0
                try {
                    while (isActive) {
                        ensureActive()
                        val read = recorder.read(pcm, 0, minOf(pcm.size, ring.samplesUntilWindow), AudioRecord.READ_NON_BLOCKING)
                        if (read < 0) throw AudioCaptureException("O microfone foi interrompido. Tente novamente.")
                        if (read == 0) { delay(5); continue }
                        if (ring.append(pcm, read)) {
                            if (Build.VERSION.SDK_INT >= 29 && checks++ % 20 == 0 &&
                                recorder.activeRecordingConfiguration?.isClientSilenced == true) {
                                throw AudioCaptureException("O microfone está indisponível ou bloqueado pelo sistema.")
                            }
                            val available = metrics.nowNs()
                            val window = free.tryReceive().getOrNull()
                            if (window == null) { metrics.droppedWindow(); continue }
                            ring.copyWindow(window.samples)
                            window.availableNs = available
                            if (!pending.trySend(window).isSuccess) free.trySend(window)
                        }
                    }
                } finally {
                    pending.cancel()
                    worker.cancel()
                }
            }
        } finally {
            for (effect in effects) {
                try { effect.release() } catch (_: RuntimeException) { /* Still release the recorder. */ }
            }
            try { recorder.stop() } catch (_: IllegalStateException) { /* Already stopped. */ }
            recorder.release()
        }
    }

    private fun openRecorder(): AudioRecord {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val sources = if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true")
            listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        else listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        for (source in sources) for (sampleRate in listOf(44_100, 48_000)) {
            val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) continue
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord.Builder().setAudioSource(source)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minimum * 2, config.windowSize * 2)).build()
                if (recorder.state != AudioRecord.STATE_INITIALIZED) continue
                recorder.startRecording()
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) return recorder
            } catch (exception: SecurityException) {
                throw exception
            } catch (_: IllegalArgumentException) {
                // Try the next supported rate/source.
            } catch (_: IllegalStateException) {
                // Try the next supported rate/source.
            } catch (_: UnsupportedOperationException) {
                // Some devices reject unsupported configurations from Builder.build().
            } finally {
                if (recorder?.recordingState != AudioRecord.RECORDSTATE_RECORDING) recorder?.release()
            }
        }
        throw AudioCaptureException("Não foi possível iniciar o microfone. Feche outros apps de áudio e tente novamente.")
    }

}
