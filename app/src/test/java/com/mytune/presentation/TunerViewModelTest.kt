package com.mytune.presentation

import com.mytune.data.audio.*
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var source: TestSource
    private lateinit var model: TunerViewModel
    private class TestSource : AudioCaptureSource {
        val frames = Channel<FloatArray>(Channel.UNLIMITED)
        var owners = 0
        var maxOwners = 0
        var releases = 0
        override suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit) {
            owners++
            maxOwners = maxOf(maxOwners, owners)
            try {
                onStarted(44_100)
                for (samples in frames) onSamples(samples, 44_100)
            } finally { owners--; releases++ }
        }
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        source = TestSource()
        model = TunerViewModel(source, PitchDetector { samples, _ ->
            if (samples[0] == 0f) PitchDetection() else
                PitchDetection(samples[0].toDouble(), .99, .2, SignalQuality.PITCHED)
        }, nowMs = { dispatcher.scheduler.currentTime })
    }
    @After fun teardown() { model.stop(); Dispatchers.resetMain() }
    private fun frame(frequency: Double) { source.frames.trySend(floatArrayOf(frequency.toFloat())).getOrThrow() }
    private fun modelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { model.stop(); runCurrent() }
    }

    @Test fun `selecting a string changes exact target and clears previous result`() = modelTest {
        model.start(true); runCurrent()
        frame(StandardTuning.strings[0].frequency); runCurrent()
        assertEquals(TuningStatus.IN_TUNE, model.uiState.value.result?.status)
        model.selectString(StandardTuning.strings[1])
        assertEquals(110.0, model.uiState.value.selectedString!!.frequency, .00001)
        assertNull(model.uiState.value.result)
        assertTrue(model.uiState.value.microphoneActive)
        frame(StandardTuning.strings[0].frequency); runCurrent()
        assertEquals("E2", model.uiState.value.result?.note?.label)
        assertEquals(TuningStatus.WRONG_NOTE, model.uiState.value.result?.status)
        model.stop(); runCurrent()
    }

    @Test fun `rapid selections remain manual and next frame uses latest target`() = modelTest {
        model.start(true); runCurrent()
        StandardTuning.strings.forEach(model::selectString)
        frame(StandardTuning.strings.last().frequency); runCurrent()
        assertEquals(1, model.uiState.value.selectedString!!.number)
        assertEquals(TuningStatus.IN_TUNE, model.uiState.value.result?.status)
        repeat(2) { frame(110.0); runCurrent() }
        assertEquals(1, model.uiState.value.selectedString!!.number)
        assertEquals(TuningStatus.WRONG_NOTE, model.uiState.value.result?.status)
        model.stop(); runCurrent()
    }

    @Test fun `brief silence preserves pitch but updates raw signal and level`() = modelTest {
        model.start(true); runCurrent()
        frame(80.0); runCurrent()
        val result = model.uiState.value.result
        frame(0.0); runCurrent()
        advanceTimeBy(300); runCurrent()
        assertEquals(result, model.uiState.value.result)
        // 80 Hz is just below the E2 semitone boundary; keep the real note identity.
        assertEquals(TuningStatus.WRONG_NOTE, model.uiState.value.result?.status)
        assertTrue(model.uiState.value.result!!.cents < 0)
        assertEquals(ReadingStatus.HELD, model.uiState.value.readingStatus)
        assertEquals(0f, model.uiState.value.audioLevel)
        assertEquals(SignalQuality.SILENCE, model.uiState.value.signalQuality)
        model.stop(); runCurrent()
    }

    @Test fun `clock holds fades and expires pitch even if no more buffers arrive`() = modelTest {
        model.start(true); runCurrent()
        frame(80.0); runCurrent()
        val result = model.uiState.value.result
        advanceTimeBy(1000); runCurrent()
        assertEquals(result, model.uiState.value.result)
        assertEquals(ReadingStatus.HELD, model.uiState.value.readingStatus)
        advanceTimeBy(200); runCurrent()
        assertEquals(result, model.uiState.value.result)
        assertEquals(ReadingStatus.STALE, model.uiState.value.readingStatus)
        advanceTimeBy(600); runCurrent()
        assertNull(model.uiState.value.result)
        assertEquals(ReadingStatus.ABSENT, model.uiState.value.readingStatus)
        assertTrue(model.uiState.value.microphoneActive)
        model.stop(); runCurrent()
    }

    @Test fun `new detection during hold refreshes without empty result`() = modelTest {
        model.start(true); runCurrent()
        frame(80.5); runCurrent()
        advanceTimeBy(1300); runCurrent()
        frame(81.0); runCurrent()
        assertNotNull(model.uiState.value.result)
        assertEquals(ReadingStatus.ACTIVE, model.uiState.value.readingStatus)
        assertEquals(1300L, model.uiState.value.lastValidDetectionMs)
        advanceTimeBy(1000); runCurrent()
        assertNotNull(model.uiState.value.result)
        model.stop(); runCurrent()
    }

    @Test fun `prolonged silence never invents a note`() = modelTest {
        model.start(true); runCurrent()
        repeat(50) { frame(0.0); advanceTimeBy(100); runCurrent() }
        assertNull(model.uiState.value.result)
        assertEquals(ReadingStatus.ABSENT, model.uiState.value.readingStatus)
        model.stop(); runCurrent()
    }

    @Test fun `pause cancels expiry and resume never restores old result`() = modelTest {
        model.start(true); runCurrent()
        frame(82.41); runCurrent()
        model.stop(); runCurrent()
        advanceTimeBy(3000); runCurrent()
        assertNull(model.uiState.value.result)
        assertNull(model.uiState.value.lastValidDetectionMs)
        model.start(true); runCurrent()
        assertNull(model.uiState.value.result)
        assertEquals(1, source.owners)
        assertEquals(1, source.maxOwners)
        model.stop(); runCurrent()
    }

    @Test fun `capture completing normally clears active result and expiry job`() = modelTest {
        model.start(true); runCurrent()
        frame(82.41); runCurrent()
        source.frames.close(); runCurrent()
        assertFalse(model.uiState.value.microphoneActive)
        assertNull(model.uiState.value.result)
        advanceUntilIdle()
    }

    @Test fun `selection during detection rejects the in-flight old-string buffer`() = modelTest {
        val changing = TunerViewModel(source, PitchDetector { _, _ ->
            model.selectString(StandardTuning.strings[1])
            PitchDetection(82.41, .99, .2, SignalQuality.PITCHED)
        }, nowMs = { dispatcher.scheduler.currentTime })
        model = changing
        model.start(true); runCurrent()
        frame(82.41); runCurrent()
        assertEquals(StandardTuning.strings[1], model.uiState.value.selectedString)
        assertNull(model.uiState.value.result)
        model.stop(); runCurrent()
    }

    @Test fun `permission denial and granting again control capture`() = modelTest {
        model.start(false); runCurrent()
        assertEquals(0, source.owners)
        model.start(true); runCurrent()
        assertTrue(model.uiState.value.microphoneActive)
        model.start(false); runCurrent()
        assertFalse(model.uiState.value.microphoneActive)
        assertEquals(1, source.releases)
        model.start(true); runCurrent()
        assertTrue(model.uiState.value.microphoneActive)
        model.stop(); runCurrent()
    }

    @Test fun `rapid pause resume releases recorder before opening another`() = modelTest {
        model.start(true); runCurrent()
        model.start(true); runCurrent()
        assertEquals(1, source.owners)
        repeat(6) { model.stop(); model.start(true) }
        runCurrent()
        assertEquals(1, source.maxOwners)
        assertEquals(1, source.owners)
        model.stop(); runCurrent()
        assertEquals(0, source.owners)
        assertNull(model.uiState.value.result)
    }

    @Test fun `revoked permission during capture becomes recoverable UI state`() = modelTest {
        val failing = TunerViewModel(object : AudioCaptureSource {
            override suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit) {
                throw SecurityException("revoked")
            }
        })
        failing.start(true); runCurrent()
        assertFalse(failing.uiState.value.hasPermission)
        assertFalse(failing.uiState.value.microphoneActive)
        assertNotNull(failing.uiState.value.error)
        failing.stop()
    }

    @Test fun `capture failure is shown and permits retry`() = modelTest {
        val failing = TunerViewModel(object : AudioCaptureSource {
            override suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit) {
                throw AudioCaptureException("Microfone ocupado")
            }
        })
        failing.start(true); runCurrent()
        assertEquals("Microfone ocupado", failing.uiState.value.error)
        assertFalse(failing.uiState.value.microphoneActive)
        failing.stop()
    }

    @Test fun `late native startup callback cannot reactivate a paused screen`() = modelTest {
        val ready = CompletableDeferred<Unit>()
        val late = TunerViewModel(object : AudioCaptureSource {
            override suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit) {
                withContext(NonCancellable) { ready.await(); onStarted(44_100) }
            }
        })
        late.start(true); runCurrent()
        late.stop()
        ready.complete(Unit); runCurrent()
        assertFalse(late.uiState.value.microphoneActive)
        assertNull(late.uiState.value.sampleRate)
    }

    @Test fun `smoothing never keeps green when the actual pitch leaves tolerance`() = modelTest {
        model.start(true); runCurrent()
        val target = StandardTuning.strings.first().frequency
        repeat(3) { frame(target); runCurrent() }
        assertEquals(TuningStatus.IN_TUNE, model.uiState.value.result?.status)
        // The median still contains the tuned note, but the current sample is sharp.
        frame(target * 2.0.pow(10.0 / 1200)); runCurrent()
        assertEquals(TuningStatus.SHARP, model.uiState.value.result?.status)
        model.stop(); runCurrent()
    }
}
