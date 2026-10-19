package com.mytune

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mytune.data.audio.AudioCaptureSource
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import com.mytune.presentation.*
import com.mytune.ui.screens.TunerScreen
import com.mytune.ui.theme.MyTuneTheme
import com.mytune.ui.components.LocalMotionEnabled
import kotlinx.coroutines.awaitCancellation
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/** Real ViewModel/Compose integration, controlled pitch/time; acoustic accuracy is tested separately. */
@RunWith(AndroidJUnit4::class)
class AutomaticTunerScreenTest {
    @get:Rule val compose = createComposeRule()
    private val time = AtomicLong(0)
    private class Source : AudioCaptureSource {
        var starts = 0
        var samples: ((FloatArray, Int) -> Unit)? = null
        override suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit) {
            starts++; samples = onSamples
            try { onStarted(44_100); awaitCancellation() } finally { samples = null }
        }
    }
    private val source = Source()
    private val model = TunerViewModel(source, PitchDetector { pcm, _ ->
        if (pcm[0] == 0f) PitchDetection() else PitchDetection(pcm[0].toDouble(), .99, .2, SignalQuality.PITCHED)
    }, nowMs = time::get)

    @Before fun setup() {
        compose.setContent {
            val state by model.uiState.collectAsState()
            CompositionLocalProvider(LocalMotionEnabled provides false) { MyTuneTheme { TunerScreen(state, model::selectString, {}, onModeChange = model::setTuningMode) } }
        }
        compose.runOnIdle { model.start(true) }
        compose.waitUntil { source.samples != null }
    }
    @After fun cleanup() { compose.runOnIdle { model.stop() } }
    private fun emit(hz: Double, count: Int = 1) {
        repeat(count) {
            compose.runOnIdle { time.addAndGet(46); source.samples!!(floatArrayOf(hz.toFloat()), 44_100) }
            compose.waitForIdle()
        }
    }
    private fun automatic() { compose.onNodeWithTag("mode_AUTOMATIC").performScrollTo().performClick() }
    private fun assertStringsSelected(count: Int) {
        compose.onAllNodes(isSelected() and hasAnyAncestor(hasTestTag("guitarSection"))).assertCountEquals(count)
    }

    @Test fun automaticControlWaitsForConfirmationHighlightsStringsAndTapReturnsToManual() {
        automatic()
        compose.onNodeWithTag("mode_AUTOMATIC").assertIsSelected()
        assertStringsSelected(0)
        compose.onNodeWithText("AGUARDANDO CORDA").assertExists()
        emit(82.406889, 3)
        assertStringsSelected(0)
        compose.onNodeWithText("Afinada").assertDoesNotExist()
        emit(82.406889)
        compose.onNodeWithTag("guitarString_6").assertIsSelected()
        compose.onNodeWithText("6ª CORDA · E2").assertExists()
        emit(110.0, 4)
        compose.onNodeWithTag("guitarString_5").assertIsSelected()
        assertStringsSelected(1)
        compose.onNodeWithText("Alvo A2 · 110,00 Hz").assertExists()
        compose.onNodeWithTag("guitarString_5").performScrollTo().performClick()
        compose.onNodeWithTag("mode_MANUAL").assertIsSelected()
        assertEquals(TuningMode.MANUAL, model.uiState.value.tuningMode)
        emit(82.406889, 4)
        compose.onNodeWithTag("guitarString_5").assertIsSelected()
        assertEquals(1, source.starts)
    }

    @Test fun anUnmatchedOrSilentSignalNeverShowsOldTuningGuidanceAsCurrent() {
        automatic(); emit(82.406889, 4)
        compose.onNodeWithText("Afinada").assertExists()
        emit(440.0)
        compose.onNodeWithText("Sem correspondência · Use o modo Manual").assertExists()
        compose.onNodeWithText("Afinada").assertDoesNotExist()
        assertEquals(ReadingStatus.HELD, model.uiState.value.readingStatus)
        emit(0.0)
        compose.onNodeWithText("Última leitura · Aguardando corda").assertExists()
        compose.runOnIdle { time.addAndGet(1900) }
        compose.waitUntil { model.uiState.value.selectedString == null }
        compose.onNodeWithText("Toque uma corda").assertExists()
        compose.onNodeWithText("— Hz").assertExists()
        assertStringsSelected(0)
        assertEquals(1, source.starts)
    }
}
