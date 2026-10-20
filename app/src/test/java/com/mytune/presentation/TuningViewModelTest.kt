package com.mytune.presentation

import androidx.datastore.core.DataStore
import androidx.lifecycle.*
import com.mytune.data.audio.AudioCaptureSource
import com.mytune.data.tuning.*
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import com.mytune.domain.tuning.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class TuningViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val vmStore = ViewModelStore()
    private class Store : DataStore<TuningSettings> {
        val values = MutableStateFlow(TuningSettings())
        var failWrites = false
        override val data: Flow<TuningSettings> = values
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (TuningSettings) -> TuningSettings): TuningSettings = mutex.withLock {
            if (failWrites) throw IOException("disk error")
            transform(values.value).also { values.value = it }
        }
    }
    private class Source : AudioCaptureSource {
        val frames = Channel<FloatArray>(Channel.UNLIMITED)
        var starts = 0; var owners = 0
        override suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit) {
            starts++; owners++
            try { onStarted(44_100); for (frame in frames) onSamples(frame,44_100) } finally { owners-- }
        }
    }
    private lateinit var store: Store
    private lateinit var source: Source
    private lateinit var model: TunerViewModel
    private var duringDetection: (() -> Unit)? = null
    @Before fun setup() {
        Dispatchers.setMain(dispatcher); store = Store(); source = Source()
        val built = TunerViewModel(source, PitchDetector { pcm, _ ->
            duringDetection?.invoke()
            if (pcm[0] == 0f) PitchDetection() else PitchDetection(pcm[0].toDouble(),.99,.2,SignalQuality.PITCHED)
        }, nowMs = { dispatcher.scheduler.currentTime }, tuningRepository = TuningRepository(store))
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = built as T
        }
        model = ViewModelProvider(vmStore, factory)[TunerViewModel::class.java]
    }
    @After fun cleanup() { vmStore.clear(); Dispatchers.resetMain() }
    private fun checkModel(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { runCurrent(); model.start(true); runCurrent(); block() }
        finally { model.stop(); runCurrent() }
    }
    private fun TestScope.frame(hz: Double) {
        source.frames.trySend(floatArrayOf(hz.toFloat())).getOrThrow(); runCurrent(); advanceTimeBy(46); runCurrent()
    }
    @Test fun `all nine tunings preserve physical manual selection and calculate current targets`() = checkModel {
        for (tuning in TuningPresets.all) {
            assertTrue(model.selectTuning(tuning.id)); runCurrent()
            assertEquals(tuning,model.uiState.value.activeTuning)
            for (string in tuning.strings) for (cents in listOf(-20,0,20)) {
                model.selectString(string)
                repeat(12) { frame(string.frequency * 2.0.pow(cents/1200.0)) }
                val state = model.uiState.value
                assertEquals(string,state.selectedString)
                assertEquals(cents.toDouble(),state.result!!.cents,.1)
                assertEquals(if (cents<0) TuningStatus.FLAT else if (cents>0) TuningStatus.SHARP else TuningStatus.IN_TUNE,state.result.status)
            }
        }
        assertEquals(1,source.starts)
    }
    @Test fun `all presets reclassify every string without waiting for old hold or restarting capture`() = checkModel {
        model.setTuningMode(TuningMode.AUTOMATIC)
        repeat(4) { frame(82.406889) }
        for (tuning in TuningPresets.all.drop(1) + TuningPresets.standard) {
            model.selectTuning(tuning.id); runCurrent()
            assertNull(model.uiState.value.selectedString); assertNull(model.uiState.value.result)
            for (string in model.uiState.value.activeTuning.strings) {
                repeat(4) { frame(string.frequency) }
                assertEquals(string,model.uiState.value.selectedString)
                assertEquals(0.0,model.uiState.value.result!!.cents,.001)
            }
        }
        model.selectTuning("drop-c"); runCurrent()
        repeat(45) { frame(82.406889) } // Still physically E2, cannot pretend it is C2.
        assertNull(model.uiState.value.result); assertNull(model.uiState.value.selectedString)
        assertEquals(1,source.starts)
    }
    @Test fun `custom creation edits renames and active deletion persist and invalidate incompatible results`() = checkModel {
        assertTrue(model.saveCustomTuning(null,"Pessoal",listOf(36,43,48,53,57,62)))
        runCurrent()
        val custom = model.uiState.value.activeTuning
        assertTrue(custom.isCustom); assertTrue(custom.id.startsWith("custom-"))
        frame(custom.string(6).frequency)
        assertNotNull(model.uiState.value.result)
        assertTrue(model.saveCustomTuning(custom.id,"Renomeada",listOf(38,43,48,53,57,62))); runCurrent()
        assertEquals(6,model.uiState.value.selectedString!!.number)
        assertEquals(38,model.uiState.value.selectedString!!.midi)
        assertNull(model.uiState.value.result)
        assertEquals("Renomeada",store.values.value.active.name)
        model.selectString(model.uiState.value.activeTuning.string(2))
        model.selectTuning("open-a"); runCurrent()
        assertEquals(2,model.uiState.value.selectedString!!.number)
        assertEquals(61,model.uiState.value.selectedString!!.midi)
        model.selectTuning(custom.id); model.deleteCustomTuning(custom.id); runCurrent()
        assertEquals(TuningPresets.standard,model.uiState.value.activeTuning)
        assertEquals(TuningPresets.standard.id,store.values.value.selectedId)
        assertNull(model.uiState.value.result); assertEquals(1,source.starts)
    }
    @Test fun `unison and near pitches never choose arbitrary automatic strings but manual still works`() = checkModel {
        model.saveCustomTuning(null,"Uníssono",listOf(40,40,50,55,59,64)); runCurrent()
        model.setTuningMode(TuningMode.AUTOMATIC)
        repeat(8) { frame(82.406889) }
        assertNull(model.uiState.value.selectedString); assertFalse(model.uiState.value.hasTuningGuidance)
        assertEquals(StringClassificationStatus.AMBIGUOUS,model.uiState.value.stringClassification!!.status)
        model.selectString(model.uiState.value.activeTuning.string(5)); frame(82.406889)
        assertEquals(5,model.uiState.value.selectedString!!.number)
        assertEquals(TuningStatus.IN_TUNE,model.uiState.value.result!!.status)
    }
    @Test fun `failed persistence preserves library and reports a recoverable error without stopping audio`() = checkModel {
        model.saveCustomTuning(null,"Guardada",listOf(36,43,48,53,57,62)); runCurrent()
        val before = store.values.value
        store.failWrites = true
        assertFalse(model.selectTuning("drop-d")); runCurrent()
        assertEquals(before,store.values.value)
        assertNotNull(model.uiState.value.tuningStorageError)
        assertTrue(model.uiState.value.microphoneActive)
        store.failWrites = false
        assertTrue(model.selectTuning("drop-d")); runCurrent()
        assertEquals("drop-d",model.uiState.value.activeTuning.id)
        assertEquals(1,model.uiState.value.availableTunings.count { it.isCustom })
        assertFalse(model.deleteCustomTuning("drop-d"))
        assertFalse(model.saveCustomTuning(null," ",listOf(40,45,50,55,59,64)))
        assertEquals(1,source.starts)
    }
    @Test fun `serialized rapid changes and older emissions cannot restore an outdated tuning`() = checkModel {
        val old = store.values.value
        val jobs = listOf(async { model.selectTuning("drop-d") },async { model.selectTuning("drop-c") })
        jobs.awaitAll(); runCurrent()
        assertEquals("drop-c",model.uiState.value.activeTuning.id)
        assertEquals("drop-c",store.values.value.selectedId)
        store.values.value = old; runCurrent()
        assertEquals("drop-c",model.uiState.value.activeTuning.id)
        assertEquals(1,source.starts)
    }
    @Test fun `restored tuning loads before any pitch is applied and recovery does not clear valid data`() = checkModel {
        val restored = TuningValidator.custom("restored","Restaurada",listOf(36,43,48,53,57,62))
        store.values.value = TuningSettings(restored.id,listOf(restored),10,hasRecoveryWarning=true); runCurrent()
        assertEquals(restored,model.uiState.value.activeTuning)
        assertNotNull(model.uiState.value.tuningRecoveryNotice)
        frame(restored.string(6).frequency)
        assertEquals(TuningStatus.IN_TUNE,model.uiState.value.result!!.status)
        model.stop(); runCurrent(); model.start(true); runCurrent()
        assertEquals(restored,model.uiState.value.activeTuning)
        assertNull(model.uiState.value.result)
    }
    @Test fun `tuning change during detection rejects the old tuning window`() = checkModel {
        duringDetection = {
            duringDetection = null
            runBlocking { model.selectTuning("drop-c") }
        }
        frame(82.406889)
        assertEquals("drop-c",model.uiState.value.activeTuning.id)
        assertNull(model.uiState.value.result)
        frame(TuningPresets.find("drop-c")!!.string(6).frequency)
        assertEquals(TuningStatus.IN_TUNE,model.uiState.value.result!!.status)
        assertEquals(1,source.starts)
    }

}
