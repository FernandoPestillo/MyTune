package com.mytune.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytune.data.audio.*
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.log10
import java.util.concurrent.atomic.AtomicLong

class TunerViewModel(
    private val source: AudioCaptureSource,
    private val detector: PitchDetector = YinPitchDetector(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = mutableState.asStateFlow()
    private var captureJob: Job? = null
    private val captureMutex = Mutex()
    private val generation = AtomicLong()

    fun selectString(string: GuitarStringNote) {
        if (string !in StandardTuning.strings) return
        mutableState.update { it.copy(selectedString = string, result = null) }
    }

    fun start(hasPermission: Boolean) {
        mutableState.update { it.copy(hasPermission = hasPermission) }
        if (!hasPermission) { stop(); return }
        if (captureJob?.isActive == true) return
        val session = generation.incrementAndGet()
        mutableState.update { it.copy(error = null, result = null) }
        captureJob = viewModelScope.launch {
            // A rapid pause/resume waits for the previous recorder's finally block.
            captureMutex.withLock {
                val smoother = PitchSmoother()
                try {
                    source.capture(
                        onStarted = { rate -> mutableState.update {
                            if (generation.get() != session) it else
                                it.copy(microphoneActive = true, sampleRate = rate)
                        } },
                        onSamples = { samples, rate ->
                            val detection = detector.detect(samples, rate)
                            val frequency = detection.frequency
                            val smoothed = if (frequency != null) smoother.smooth(frequency)
                                else { smoother.reset(); null }
                            val level = if (detection.rms > 0)
                                ((20 * log10(detection.rms) + 60) / 60).coerceIn(0.0, 1.0).toFloat() else 0f
                            mutableState.update { state ->
                                if (!state.microphoneActive || generation.get() != session) state else {
                                    var result = smoothed?.let {
                                        TuningCalculator.calculate(it, state.selectedString, detection.confidence)
                                    }
                                    // Smoothing must never turn an out-of-tune raw sample green.
                                    if (result?.status == TuningStatus.IN_TUNE && frequency != null) {
                                        val raw = TuningCalculator.calculate(frequency, state.selectedString, detection.confidence)
                                        if (raw.status != TuningStatus.IN_TUNE) result = raw
                                    }
                                    state.copy(result = result, signalQuality = detection.quality, audioLevel = level)
                                }
                            }
                        },
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: SecurityException) {
                    mutableState.update { if (generation.get() != session) it else it.copy(hasPermission = false, microphoneActive = false,
                        result = null, audioLevel = 0f, error = "Permita o acesso ao microfone para afinar.") }
                } catch (exception: Exception) {
                    mutableState.update { if (generation.get() != session) it else it.copy(microphoneActive = false, result = null, audioLevel = 0f,
                        error = (exception as? AudioCaptureException)?.message ?: "Falha na captação. Tente novamente.") }
                }
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        mutableState.update { it.copy(microphoneActive = false, result = null, audioLevel = 0f,
            signalQuality = SignalQuality.SILENCE, sampleRate = null) }
        captureJob?.cancel()
        captureJob = null
    }
}
