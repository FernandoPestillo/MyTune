package com.mytune.presentation

import com.mytune.data.audio.AudioCaptureSource
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticTunerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var source: TestSource
    private lateinit var model: TunerViewModel
    private val e2 = StandardTuning.strings.first()
    private class TestSource : AudioCaptureSource {
        val frames = Channel<FloatArray>(Channel.UNLIMITED)
        var starts = 0; var owners = 0; var maxOwners = 0
        override suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit) {
            starts++; owners++; maxOwners = maxOf(owners, maxOwners)
            try { onStarted(44_100); for (frame in frames) onSamples(frame, 44_100) }
            finally { owners-- }
        }
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        source = TestSource()
        model = TunerViewModel(source, PitchDetector { samples, _ ->
            if (samples[0] == 0f) PitchDetection() else PitchDetection(samples[0].toDouble(),
                samples[1].toDouble(), samples[2].toDouble(), SignalQuality.entries[samples[3].toInt()])
        }, nowMs = { dispatcher.scheduler.currentTime })
    }
    @After fun teardown() { model.stop(); Dispatchers.resetMain() }
    private fun checkModel(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { model.stop(); runCurrent() }
    }
    private fun TestScope.frame(hz: Double, confidence: Double = .99, quality: SignalQuality = SignalQuality.PITCHED,
        rms: Double = .2, stepMs: Long = 46) {
        source.frames.trySend(floatArrayOf(hz.toFloat(), confidence.toFloat(), rms.toFloat(), quality.ordinal.toFloat())).getOrThrow()
        runCurrent(); advanceTimeBy(stepMs); runCurrent()
    }
    private fun TestScope.startAuto() { model.start(true); runCurrent(); model.setTuningMode(TuningMode.AUTOMATIC) }
    private fun TestScope.confirm(hz: Double, count: Int = 4) { repeat(count) { frame(hz) } }

    @Test fun `manual remains default and mode switches reuse the same capture`() = checkModel {
        assertEquals(TuningMode.MANUAL, model.uiState.value.tuningMode)
        model.start(true); runCurrent()
        frame(e2.frequency)
        assertEquals(e2, model.uiState.value.selectedString)
        assertNotNull(model.uiState.value.result)
        model.setTuningMode(TuningMode.AUTOMATIC)
        assertNull(model.uiState.value.selectedString); assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.hasTuningGuidance)
        confirm(110.0)
        assertEquals(StandardTuning.strings[1], model.uiState.value.selectedString)
        model.setTuningMode(TuningMode.MANUAL)
        assertEquals(e2, model.uiState.value.selectedString)
        assertNull(model.uiState.value.result)
        frame(110.0)
        assertEquals(e2, model.uiState.value.selectedString)
        assertEquals(TuningStatus.WRONG_NOTE, model.uiState.value.result!!.status)
        assertEquals(1, source.starts); assertEquals(1, source.maxOwners)
    }
    @Test fun `all references and detunings confirm the right target without correcting measured pitch`() = checkModel {
        startAuto()
        for (string in StandardTuning.strings) for (cents in listOf(-100, -50, -20, 0, 20, 50, 100)) {
            model.setTuningMode(TuningMode.MANUAL); model.setTuningMode(TuningMode.AUTOMATIC)
            val hz = string.frequency * 2.0.pow(cents / 1200.0)
            confirm(hz)
            val state = model.uiState.value
            assertEquals(string, state.selectedString)
            assertEquals(AutomaticSelectionStatus.TRACKING, state.automaticSelectionStatus)
            assertEquals(cents.toDouble(), state.result!!.cents, .001)
            assertEquals(hz, state.result.frequency, .001)
        }
        assertEquals(1, source.starts)
    }
    @Test fun `sequential strings update target and result atomically before previous hold expires`() = checkModel {
        startAuto()
        var previous: GuitarStringNote? = null
        for (string in StandardTuning.strings) {
            repeat(3) {
                frame(string.frequency)
                assertEquals(previous, model.uiState.value.selectedString)
                if (previous != null) {
                    assertEquals(previous.label, model.uiState.value.result!!.note.label)
                    assertFalse(model.uiState.value.hasTuningGuidance)
                }
            }
            frame(string.frequency)
            val state = model.uiState.value
            assertEquals(string, state.selectedString)
            assertEquals(string.label, state.result!!.note.label)
            assertEquals(0.0, state.result.cents, .001)
            assertEquals(ReadingStatus.ACTIVE, state.readingStatus)
            previous = string
        }
        assertEquals(1, source.starts)
    }
    @Test fun `isolated E3 or E4 reading never steals E2 selection`() = checkModel {
        startAuto(); confirm(e2.frequency)
        val original = model.uiState.value.result
        for (outlier in listOf(e2.frequency * 2, e2.frequency * 4)) {
            frame(outlier)
            assertEquals(e2, model.uiState.value.selectedString)
            assertEquals(original, model.uiState.value.result)
            assertFalse(model.uiState.value.hasTuningGuidance)
            frame(e2.frequency)
            assertEquals(e2, model.uiState.value.selectedString)
            assertTrue(model.uiState.value.hasTuningGuidance)
        }
        confirm(StandardTuning.strings.last().frequency, count = 5)
        assertEquals(StandardTuning.strings.last(), model.uiState.value.selectedString)
    }
    @Test fun `silence of 200 and 800 ms retains target then timeout removes automatic highlight`() = checkModel {
        startAuto(); confirm(e2.frequency)
        val before = model.uiState.value.result
        frame(0.0, stepMs = 200)
        assertEquals(e2, model.uiState.value.selectedString); assertEquals(before, model.uiState.value.result)
        assertEquals(ReadingStatus.HELD, model.uiState.value.readingStatus)
        assertFalse(model.uiState.value.hasTuningGuidance)
        advanceTimeBy(600); runCurrent()
        assertEquals(e2, model.uiState.value.selectedString)
        advanceTimeBy(400); runCurrent()
        assertEquals(ReadingStatus.STALE, model.uiState.value.readingStatus)
        advanceTimeBy(700); runCurrent()
        assertNull(model.uiState.value.selectedString); assertNull(model.uiState.value.result)
        assertEquals(AutomaticSelectionStatus.WAITING, model.uiState.value.automaticSelectionStatus)
        confirm(110.0)
        assertEquals(StandardTuning.strings[1], model.uiState.value.selectedString)
        assertEquals(1, source.starts)
    }
    @Test fun `invalid and out of range signals never select or renew an old reading`() = checkModel {
        startAuto()
        for (hz in listOf(0.0, -110.0, Double.NaN, Double.POSITIVE_INFINITY, 55.0, 440.0, 2000.0)) {
            confirm(hz)
            assertNull(model.uiState.value.selectedString); assertNull(model.uiState.value.result)
        }
        repeat(4) { frame(110.0, confidence = .8) }
        repeat(4) { frame(110.0, quality = SignalQuality.UNRELIABLE) }
        assertNull(model.uiState.value.selectedString)
        confirm(e2.frequency)
        val lastValid = model.uiState.value.lastValidDetectionMs
        repeat(45) { frame(440.0) }
        assertNull(model.uiState.value.result); assertNull(model.uiState.value.selectedString)
        assertNotEquals(lastValid, model.uiState.value.lastValidDetectionMs)
        assertFalse(model.uiState.value.hasTuningGuidance)
    }
    @Test fun `alternating candidates never confirm or flap an existing selection`() = checkModel {
        startAuto()
        repeat(12) { frame(if (it % 2 == 0) e2.frequency else 110.0) }
        assertNull(model.uiState.value.selectedString)
        confirm(e2.frequency)
        repeat(12) { frame(if (it % 2 == 0) 110.0 else e2.frequency) }
        assertEquals(e2, model.uiState.value.selectedString)
    }
    @Test fun `tap in automatic mode becomes an explicit manual selection even on same string`() = checkModel {
        startAuto(); confirm(110.0)
        model.selectString(StandardTuning.strings[1])
        assertEquals(TuningMode.MANUAL, model.uiState.value.tuningMode)
        assertEquals(StandardTuning.strings[1], model.uiState.value.selectedString)
        assertNull(model.uiState.value.result)
        confirm(e2.frequency)
        assertEquals(StandardTuning.strings[1], model.uiState.value.selectedString)
        assertEquals(1, source.starts)
    }
    @Test fun `fast mode toggles and lifecycle discard partial automatic evidence`() = checkModel {
        startAuto(); repeat(3) { frame(110.0) }
        repeat(10) { model.setTuningMode(TuningMode.MANUAL); model.setTuningMode(TuningMode.AUTOMATIC) }
        frame(110.0)
        assertNull(model.uiState.value.selectedString)
        model.stop(); runCurrent()
        model.start(true); runCurrent()
        assertEquals(TuningMode.AUTOMATIC, model.uiState.value.tuningMode)
        assertNull(model.uiState.value.selectedString)
        frame(110.0)
        assertNull(model.uiState.value.selectedString)
        confirm(110.0)
        assertEquals(StandardTuning.strings[1], model.uiState.value.selectedString)
        assertEquals(1, source.maxOwners); assertEquals(2, source.starts)
    }
    @Test fun `mode switch during detector callback rejects the in flight old mode window`() = checkModel {
        var switch = true
        model = TunerViewModel(source, PitchDetector { _, _ ->
            if (switch) { switch = false; model.setTuningMode(TuningMode.AUTOMATIC) }
            PitchDetection(110.0, .99, .2, SignalQuality.PITCHED)
        }, nowMs = { dispatcher.scheduler.currentTime })
        model.start(true); runCurrent()
        frame(110.0)
        assertNull(model.uiState.value.selectedString); assertNull(model.uiState.value.result)
        repeat(3) { frame(110.0) }
        assertNull(model.uiState.value.selectedString)
        frame(110.0)
        assertEquals(StandardTuning.strings[1], model.uiState.value.selectedString)
    }
}
