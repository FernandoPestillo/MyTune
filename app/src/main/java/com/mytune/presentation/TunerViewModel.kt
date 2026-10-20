package com.mytune.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytune.data.audio.*
import com.mytune.diagnostics.AudioPerformanceMetrics
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import com.mytune.domain.tuning.*
import com.mytune.data.tuning.*
import java.io.IOException
import java.util.UUID
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
    automaticSelectionConfig: AutomaticSelectionConfig = AutomaticSelectionConfig(),
    classifierConfig: StringClassifierConfig = StringClassifierConfig(),
    private val tuningRepository: TuningRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TunerUiState(tuningsLoaded = tuningRepository == null))
    val uiState: StateFlow<TunerUiState> = mutableState.asStateFlow()
    private var captureJob: Job? = null
    private val captureMutex = Mutex()
    // Capture callbacks run on the audio processing worker; selection and lifecycle events run on Main.
    private val stateLock = Any()
    private val stabilizer = DetectionStabilizer(stabilityConfig)
    private val automaticSelector = AutomaticStringSelector(automaticSelectionConfig)
    private var lastManualStringNumber = 6
    private val stringClassifierConfig = classifierConfig
    private var classifier = GuitarStringClassifier(TuningPresets.standard.strings, classifierConfig)
    private val tuningMutationMutex = Mutex()
    private var libraryRevision = -1L
    private var pendingActiveTuningChange = false
    private var lastAutomaticInputMs: Long? = null
    private var generation = 0L
    private var selectionRevision = 0L

    init {
        tuningRepository?.let { repository ->
            viewModelScope.launch {
                try {
                    repository.settings.collect { settings -> synchronized(stateLock) { applySettings(settings) } }
                } catch (exception: CancellationException) { throw exception }
                catch (_: IOException) {
                    synchronized(stateLock) { mutableState.value = mutableState.value.copy(tuningsLoaded = true,
                        tuningStorageError = "Não foi possível ler as afinações salvas. As alterações poderão ser tentadas novamente.") }
                }
            }
        }
    }

    private fun applySettings(settings: TuningSettings) {
        if (settings.revision < libraryRevision) return
        libraryRevision = settings.revision
        var state = mutableState.value.copy(availableTunings = settings.available, tuningsLoaded = true,
            tuningRecoveryNotice = if (settings.hasRecoveryWarning)
                "Alguns dados de afinação inválidos foram ignorados. Os demais foram preservados." else null)
        if (settings.active != state.activeTuning) {
            selectionRevision++
            resetSelectionPolicies()
            classifier = GuitarStringClassifier(settings.active.strings, stringClassifierConfig)
            state = state.copy(activeTuning = settings.active).cleared()
        }
        mutableState.value = state
    }

    suspend fun selectTuning(id: String): Boolean = mutateTunings({ it.activeTuning.id != id }) { it.select(id) }

    suspend fun saveCustomTuning(id: String?, name: String, midi: List<Int>): Boolean {
        val tuning = TuningValidator.custom(id ?: "custom-${UUID.randomUUID()}", name, midi)
        val error = TuningValidator.error(tuning)
        if (error != null) {
            synchronized(stateLock) { mutableState.value = mutableState.value.copy(tuningStorageError = error) }
            return false
        }
        return mutateTunings({ id == null || it.activeTuning.id == id }) { it.save(tuning) }
    }

    /** The UI presents deletion confirmation before invoking this transaction. */
    suspend fun deleteCustomTuning(id: String): Boolean = mutateTunings({ it.activeTuning.id == id }) { it.delete(id) }

    private suspend fun mutateTunings(affectsActive: (TunerUiState) -> Boolean, operation: suspend (TuningRepository) -> TuningSettings): Boolean = tuningMutationMutex.withLock {
        val repository = tuningRepository ?: return@withLock false
        synchronized(stateLock) {
            pendingActiveTuningChange = affectsActive(mutableState.value)
            if (pendingActiveTuningChange) {
                selectionRevision++
                resetSelectionPolicies()
                mutableState.value = mutableState.value.cleared()
            }
            mutableState.value = mutableState.value.copy(tuningOperationPending = true, tuningStorageError = null)
        }
        try {
            val settings = operation(repository)
            synchronized(stateLock) { applySettings(settings) }
            true
        } catch (exception: CancellationException) { throw exception }
        catch (exception: IllegalArgumentException) {
            synchronized(stateLock) { mutableState.value = mutableState.value.copy(tuningStorageError = exception.message) }
            false
        } catch (_: IOException) {
            synchronized(stateLock) { mutableState.value = mutableState.value.copy(
                tuningStorageError = "Não foi possível salvar as afinações. Tente novamente.") }
            false
        } finally {
            synchronized(stateLock) {
                pendingActiveTuningChange = false
                mutableState.value = mutableState.value.copy(tuningOperationPending = false)
            }
        }
    }

    /** A guitar tap is always an explicit manual selection, including the highlighted auto string. */
    fun selectString(string: GuitarStringNote) = synchronized(stateLock) {
        val state = mutableState.value
        val current = state.activeTuning.strings.firstOrNull { it.number == string.number } ?: return@synchronized
        if (state.tuningMode == TuningMode.MANUAL && current == state.selectedString) return@synchronized
        selectionRevision++
        lastManualStringNumber = current.number
        resetSelectionPolicies()
        mutableState.value = state.cleared().copy(tuningMode = TuningMode.MANUAL, selectedString = current)
    }

    fun setTuningMode(mode: TuningMode) = synchronized(stateLock) {
        val state = mutableState.value
        if (mode == state.tuningMode) return@synchronized
        selectionRevision++ // Discard any detection started under the old mode/target.
        resetSelectionPolicies()
        mutableState.value = state.cleared().copy(tuningMode = mode,
            selectedString = if (mode == TuningMode.MANUAL) state.activeTuning.string(lastManualStringNumber) else null)
    }

    private fun resetSelectionPolicies() {
        stabilizer.reset()
        automaticSelector.reset()
        lastAutomaticInputMs = null
    }

    fun start(hasPermission: Boolean) = synchronized(stateLock) {
        mutableState.value = mutableState.value.copy(hasPermission = hasPermission)
        if (!hasPermission) { stop(); return@synchronized }
        if (captureJob?.isActive == true) return@synchronized
        val session = ++generation
        resetSelectionPolicies()
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
                                        advancePresentation(nowMs())
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
                                        if (state.microphoneActive && state.tuningsLoaded && !pendingActiveTuningChange && generation == session && revision == selectionRevision) {
                                            val level = if (detection.rms > 0)
                                                ((20 * log10(detection.rms) + 60) / 60).coerceIn(0.0, 1.0).toFloat() else 0f
                                            val input = state.copy(rawDetection = detection,
                                                signalQuality = detection.quality, audioLevel = level)
                                            presentDetection(detection, input, nowMs())
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
                            resetSelectionPolicies()
                            mutableState.value = mutableState.value.cleared().copy(microphoneActive = false, sampleRate = null)
                        }
                    }
                }
            }
        }
    }

    private fun presentDetection(detection: PitchDetection, input: TunerUiState, timeMs: Long) {
        if (input.tuningMode == TuningMode.MANUAL) {
            publish(stabilizer.accept(detection, checkNotNull(input.selectedString), timeMs), input)
            return
        }
        val previous = stabilizer.advance(timeMs)
        var state = if (previous.result == null) input.copy(selectedString = null) else input
        lastAutomaticInputMs = timeMs
        val classification = classifier.classify(detection)
        val decision = automaticSelector.accept(classification, state.selectedString, timeMs)
        val target = decision.acceptedString
        val presentation = if (target != null) {
            // Confirmation already happened in the selector. Publish the new target and its own
            // fresh result atomically, without averaging or retaining the previous target's data.
            if (target != state.selectedString) stabilizer.reset()
            state = state.copy(selectedString = target)
            stabilizer.accept(detection, target, timeMs)
        } else stabilizer.retain(timeMs)
        state = state.copy(
            selectedString = if (presentation.result == null) null else state.selectedString,
            stringClassification = classification,
            automaticSelectionStatus = if (detection.quality == SignalQuality.SILENCE)
                AutomaticSelectionStatus.WAITING else decision.status)
        publish(presentation, state)
    }

    private fun advancePresentation(timeMs: Long) {
        val presentation = stabilizer.advance(timeMs)
        var state = mutableState.value
        if (state.tuningMode == TuningMode.AUTOMATIC) {
            val pending = automaticSelector.advance(timeMs)
            val quiet = lastAutomaticInputMs?.let { timeMs - it >= stabilityConfig.activeReadingDurationMs } ?: true
            if (quiet) state = state.copy(stringClassification = null,
                automaticSelectionStatus = if (pending) AutomaticSelectionStatus.CONFIRMING else AutomaticSelectionStatus.WAITING)
            if (presentation.result == null) state = state.copy(selectedString = null)
        }
        publish(presentation, state)
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
        selectedString = if (tuningMode == TuningMode.MANUAL) activeTuning.string(lastManualStringNumber) else null,
        automaticSelectionStatus = AutomaticSelectionStatus.WAITING, stringClassification = null,
        rawDetection = PitchDetection(), lastValidDetectionMs = null, audioLevel = 0f, signalQuality = SignalQuality.SILENCE)

    fun stop() = synchronized(stateLock) {
        generation++
        resetSelectionPolicies()
        mutableState.value = mutableState.value.cleared().copy(microphoneActive = false, sampleRate = null)
        captureJob?.cancel()
        captureJob = null
    }
}
