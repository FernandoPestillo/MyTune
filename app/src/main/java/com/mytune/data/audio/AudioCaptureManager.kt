package com.mytune.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/** Owns exactly one recorder per call. Never writes samples to disk. */
class AudioCaptureManager(context: Context) : AudioCaptureSource {
    private val context = context.applicationContext

    override suspend fun capture(
        onStarted: (Int) -> Unit,
        onSamples: (FloatArray, Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) throw SecurityException("Permissão de microfone necessária")
        val recorder = openRecorder()
        try {
            coroutineContext.ensureActive()
            val sampleRate = recorder.sampleRate
            onStarted(sampleRate)
            val pcm = ShortArray(HOP_SIZE)
            val window = FloatArray(WINDOW_SIZE)
            var filled = 0
            var checks = 0
            while (coroutineContext.isActive) {
                coroutineContext.ensureActive()
                // Nonblocking reads make cancellation independent of native read() behavior.
                val read = recorder.read(pcm, 0, minOf(HOP_SIZE, WINDOW_SIZE - filled), AudioRecord.READ_NON_BLOCKING)
                if (read < 0) throw AudioCaptureException("O microfone foi interrompido. Tente novamente.")
                if (read == 0) { delay(5); continue }
                for (i in 0 until read) window[filled + i] = pcm[i] / 32768f
                filled += read
                if (filled == WINDOW_SIZE) {
                    if (Build.VERSION.SDK_INT >= 29 && checks++ % 20 == 0 &&
                        recorder.activeRecordingConfiguration?.isClientSilenced == true) {
                        throw AudioCaptureException("O microfone está indisponível ou bloqueado pelo sistema.")
                    }
                    onSamples(window, sampleRate)
                    window.copyInto(window, 0, HOP_SIZE, WINDOW_SIZE)
                    filled = WINDOW_SIZE - HOP_SIZE
                }
            }
        } finally {
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
                    .setBufferSizeInBytes(maxOf(minimum * 2, WINDOW_SIZE * 2)).build()
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

    companion object {
        const val WINDOW_SIZE = 4096
        const val HOP_SIZE = 2048
    }
}
