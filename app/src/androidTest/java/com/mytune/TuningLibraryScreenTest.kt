package com.mytune

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStoreFactory
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mytune.data.audio.AudioCaptureSource
import com.mytune.data.tuning.*
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import com.mytune.presentation.TunerViewModel
import com.mytune.ui.screens.TunerScreen
import com.mytune.ui.theme.MyTuneTheme
import com.mytune.ui.components.LocalMotionEnabled
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class TuningLibraryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val dataScope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val vmStore = ViewModelStore()
    private val time = AtomicLong(0)
    private lateinit var model: TunerViewModel
    private lateinit var file: java.io.File
    private class Source : AudioCaptureSource {
        var starts=0
        var callback: ((FloatArray,Int)->Unit)?=null
        override suspend fun capture(onStarted:(Int)->Unit,onSamples:(FloatArray,Int)->Unit) {
            starts++;callback=onSamples
            try { onStarted(44_100);awaitCancellation() } finally {callback=null}
        }
    }
    private val source=Source()
    @Before fun setup() {
        file=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("tuning-test-${UUID.randomUUID()}.store")
        val repo=TuningRepository(DataStoreFactory.create(TuningSettingsSerializer,scope=dataScope,produceFile={file}))
        val built=TunerViewModel(source,PitchDetector { pcm,_-> PitchDetection(pcm[0].toDouble(),.99,.2,SignalQuality.PITCHED) },
            nowMs=time::get,tuningRepository=repo)
        model=ViewModelProvider(vmStore,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=built as T
        })[TunerViewModel::class.java]
        compose.setContent {
            val state by model.uiState.collectAsState()
            CompositionLocalProvider(LocalMotionEnabled provides false) { MyTuneTheme { TunerScreen(state,model::selectString,{},onModeChange=model::setTuningMode,
                onTuningSelected=model::selectTuning,onTuningSaved=model::saveCustomTuning,onTuningDeleted=model::deleteCustomTuning) } }
        }
        compose.runOnIdle { model.start(true) }
        compose.waitUntil { model.uiState.value.tuningsLoaded && source.callback!=null }
    }
    @After fun cleanup() {
        compose.runOnIdle { model.stop();vmStore.clear() }
        runBlocking { dataScope.cancel();dataScope.coroutineContext.job.join() }
        file.delete()
    }
    private fun openLibrary() { compose.onNodeWithTag("tuningSelector").performScrollTo().performClick() }
    private fun clickLibrary(tag:String) {
        compose.onNodeWithTag("tuningLibrary").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun settled() { compose.waitUntil { !model.uiState.value.tuningOperationPending };compose.waitForIdle() }
    private fun emit(hz:Double,count:Int=1) { repeat(count) {
        compose.runOnIdle { time.addAndGet(46);source.callback!!(floatArrayOf(hz.toFloat()),44_100) };compose.waitForIdle()
    } }

    @Test fun presetsUpdateGuitarAndBothModesWithoutRestartingCapture() {
        openLibrary();clickLibrary("tuning_drop-c");settled()
        compose.onNodeWithText("Afinação: Drop C").assertExists()
        compose.onNodeWithContentDescription("6ª corda, C2").assertExists()
        emit(65.406391)
        compose.onNodeWithText("Afinada").assertExists()
        compose.onNodeWithTag("mode_AUTOMATIC").performScrollTo().performClick()
        emit(65.406391,4)
        compose.onNodeWithTag("guitarString_6").assertIsSelected()
        openLibrary();clickLibrary("tuning_drop-d");settled()
        assertNull(model.uiState.value.selectedString);assertNull(model.uiState.value.result)
        emit(73.416192,4)
        compose.onNodeWithTag("guitarString_6").assertIsSelected()
        compose.onNodeWithText("Alvo D2 · 73,42 Hz").assertExists()
        assertEquals(1,source.starts)
    }

    @Test fun editorCreatesRenamesChangesPitchAndOctaveAndConfirmsActiveDeletion() {
        openLibrary();clickLibrary("copy_drop-d")
        compose.onNodeWithTag("tuningName").performTextReplacement("Minha afinação")
        compose.onNodeWithTag("edit_note_6").performClick()
        compose.onNodeWithTag("edit_note_6_C").performClick()
        compose.onNodeWithTag("edit_octave_6").performClick()
        compose.onNodeWithTag("edit_octave_6_3").performClick()
        compose.onNodeWithTag("saveTuning").performClick()
        compose.waitUntil { model.uiState.value.activeTuning.isCustom };settled()
        val id=model.uiState.value.activeTuning.id
        assertEquals(48,model.uiState.value.activeTuning.string(6).midi)
        clickLibrary("edit_$id")
        compose.onNodeWithTag("tuningName").performTextReplacement(" ")
        compose.onNodeWithTag("saveTuning").assertIsNotEnabled()
        compose.onNodeWithTag("tuningName").performTextReplacement("Minha alterada")
        compose.onNodeWithTag("edit_note_6").performClick()
        compose.onNodeWithTag("edit_note_6_D").performClick()
        compose.onNodeWithTag("saveTuning").performClick()
        compose.waitUntil { model.uiState.value.activeTuning.name=="Minha alterada" };settled()
        assertEquals(50,model.uiState.value.activeTuning.string(6).midi)
        clickLibrary("delete_$id")
        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(model.uiState.value.availableTunings.any {it.id==id})
        clickLibrary("delete_$id")
        compose.onNodeWithTag("confirmDeleteTuning").performClick()
        compose.waitUntil { model.uiState.value.activeTuning.id=="e-standard" };settled()
        assertFalse(model.uiState.value.availableTunings.any {it.id==id})
        compose.onNodeWithContentDescription("6ª corda, E2").assertExists()
        compose.onNodeWithTag("guitarString_5").performScrollTo().performClick().assertIsSelected()
        assertEquals(1,source.starts)
    }
}
