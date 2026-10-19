package com.mytune.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytune.data.audio.*
import com.mytune.diagnostics.AudioPerformanceMetrics
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.log10

class TunerViewModel(
    private val source: AudioCaptureSource,
    private val detector: PitchDetector = YinPitchDetector(),
    private val stabilityConfig: DetectionStabilityConfig = DetectionStabilityConfig(),
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val metrics: AudioPerformanceMetrics = AudioPerformanceMetrics(),
    private val audioConfig: AudioProcessingConfig = AudioProcessingConfig(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = mutableState.asStateFlow()
    private var captureJob: Job? = null
    private val captureMutex = Mutex()
    // Capture callbacks run on the audio processing worker; selection and lifecycle events run on Main.
    private val stateLock = Any()
    private val stabilizer = DetectionStabilizer(stabilityConfig)
    private var generation = 0L
    private var selectionRevision = 0L

    fun selectString(string: GuitarStringNote) = synchronized(stateLock) {
        if (string !in StandardTuning.strings || string == mutableState.value.selectedString) return@synchronized
        selectionRevision++
        stabilizer.reset()
        mutableState.value = mutableState.value.cleared().copy(selectedString = string)
    }

    fun start(hasPermission: Boolean) = synchronized(stateLock) {
        mutableState.value = mutableState.value.copy(hasPermission = hasPermission)
        if (!hasPermission) { stop(); return@synchronized }
        if (captureJob?.isActive == true) return@synchronized
        val session = ++generation
        stabilizer.reset()
        mutableState.value = mutableState.value.cleared().copy(error = null)
        captureJob = viewModelScope.launch {
            // A rapid pause/resume waits for the previous recorder's finally block.
            captureMutex.withLock {
                metrics.reset()
                try {
                    coroutineScope {
                        // One lifecycle-owned clock also expires readings if capture stops emitting.
                        val expiryJob = launch {
                            while (isActive) {
                                delay(stabilityConfig.refreshIntervalMs)
                                synchronized(stateLock) {
                                    if (generation == session && mutableState.value.microphoneActive)
                                        publish(stabilizer.advance(nowMs()))
                                }
                            }
                        }
                        try {
                            source.capture(
                                onStarted = { rate -> synchronized(stateLock) {
                                    if (generation == session) mutableState.value = mutableState.value.copy(
                                        microphoneActive = true, sampleRate = rate)
                                } },
                                onSamples = { samples, rate ->
                                    val revision = synchronized(stateLock) { selectionRevision }
                                    val started = metrics.nowNs()
                                    val detection = detector.detect(samples, rate)
                                    metrics.detection(detection, started, metrics.nowNs(), audioConfig.hopSize * 1_000_000_000L / rate)
                                    synchronized(stateLock) {
                                        val state = mutableState.value
                                        if (state.microphoneActive && generation == session && revision == selectionRevision) {
                                            val level = if (detection.rms > 0)
                                                ((20 * log10(detection.rms) + 60) / 60).coerceIn(0.0, 1.0).toFloat() else 0f
                                            publish(stabilizer.accept(detection, state.selectedString, nowMs()),
                                                state.copy(rawDetection = detection, signalQuality = detection.quality, audioLevel = level))
                                        }
                                    }
                                },
                            )
                        } finally { expiryJob.cancel() }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: SecurityException) {
                    synchronized(stateLock) {
                        if (generation == session) mutableState.value = mutableState.value.copy(
                            hasPermission = false, error = "Permita o acesso ao microfone para afinar.")
                    }
                } catch (exception: Exception) {
                    synchronized(stateLock) {
                        if (generation == session) mutableState.value = mutableState.value.copy(
                            error = (exception as? AudioCaptureException)?.message ?: "Falha na captação. Tente novamente.")
                    }
                } finally {
                    synchronized(stateLock) {
                        if (generation == session) {
                            stabilizer.reset()
                            mutableState.value = mutableState.value.cleared().copy(microphoneActive = false, sampleRate = null)
                        }
                    }
                }
            }
        }
    }

    private fun publish(presentation: DetectionPresentation, state: TunerUiState = mutableState.value) {
        val updated = state.copy(result = presentation.result,
            readingStatus = presentation.readingStatus, lastValidDetectionMs = presentation.lastValidDetectionMs)
        if (updated != mutableState.value) {
            mutableState.value = updated
            metrics.uiUpdate()
        }
    }

    private fun TunerUiState.cleared() = copy(result = null, readingStatus = ReadingStatus.ABSENT,
        rawDetection = PitchDetection(), lastValidDetectionMs = null, audioLevel = 0f, signalQuality = SignalQuality.SILENCE)

    fun stop() = synchronized(stateLock) {
        generation++
        stabilizer.reset()
        mutableState.value = mutableState.value.cleared().copy(microphoneActive = false, sampleRate = null)
        captureJob?.cancel()
        captureJob = null
    }
}
