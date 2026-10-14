package com.mytune

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mytune.domain.model.*
import com.mytune.presentation.TunerUiState
import com.mytune.presentation.ReadingStatus
import com.mytune.domain.audio.SignalQuality
import com.mytune.domain.audio.TuningCalculator
import com.mytune.ui.screens.TunerScreen
import com.mytune.ui.theme.MyTuneTheme
import org.junit.*
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

@RunWith(AndroidJUnit4::class)
class TunerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sixStringsAreSelectableWithExactlyOneSelection() {
        compose.setContent {
            var state by remember { mutableStateOf(TunerUiState()) }
            MyTuneTheme { TunerScreen(state, { state = state.copy(selectedString = it, result = null) }, {}) }
        }
        for (string in StandardTuning.strings) {
            compose.onNodeWithContentDescription("${string.number}ª corda, ${string.label}")
                .performScrollTo().performClick().assertIsSelected()
            compose.onAllNodes(isSelected()).assertCountEquals(1)
            compose.onNodeWithText("${string.number}ª CORDA · ${string.label}").assertExists()
        }
        compose.onNodeWithText("— Hz").assertExists()
        compose.onNodeWithText("Microfone necessário").assertExists()
    }

    @Test fun detectedNoteIsIndependentOfSelectedString() {
        compose.setContent {
            MyTuneTheme {
                TunerScreen(TunerUiState(hasPermission = true, microphoneActive = true,
                    result = com.mytune.domain.audio.TuningCalculator.calculate(110.0, StandardTuning.strings.first(), .99)), {}, {})
            }
        }
        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("6ª CORDA · E2").assertExists()
        compose.onNodeWithText("Nota diferente da corda selecionada", substring = true).assertExists()
        compose.onNodeWithText("Afinada").assertDoesNotExist()
    }

    @Test fun portraitLayoutDoesNotMoveWhenReadingsOrMessagesChange() = assertStableLayout(400, 850, 1f)

    @Test fun landscapeLayoutDoesNotMoveWhenReadingsOrMessagesChange() = assertStableLayout(850, 400, 1f)

    @Test fun smallScreenWithLargeFontKeepsStableLayout() = assertStableLayout(320, 640, 1.5f)

    private fun assertStableLayout(width: Int, height: Int, fontScale: Float) {
        val base = TunerUiState(hasPermission = true, microphoneActive = true)
        var state by mutableStateOf(base)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    MyTuneTheme { TunerScreen(state, {}, {}) }
                }
            }
        }
        val tags = listOf("tunerPanel", "noteDisplay", "tuningIndicator", "tuningStatus", "guitarSection")
        compose.waitForIdle()
        val original = tags.associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        val states = listOf(80.0, 84.0, 82.406889, 110.0, 164.813778, 69.295658, 1100.0).map {
            base.copy(result = TuningCalculator.calculate(it, base.selectedString, .99), readingStatus = ReadingStatus.ACTIVE)
        }.let { readings -> readings + listOf(
            readings[0].copy(readingStatus = ReadingStatus.HELD),
            readings[0].copy(readingStatus = ReadingStatus.STALE),
            base.copy(signalQuality = SignalQuality.UNRELIABLE), base) +
            StandardTuning.strings.map { base.copy(selectedString = it) }
        }
        states.forEach { next ->
            compose.runOnIdle { state = next }
            compose.waitForIdle()
            tags.forEach { tag ->
                assertEquals("$tag moved for $next", original.getValue(tag),
                    compose.onNodeWithTag(tag).getUnclippedBoundsInRoot())
            }
            val textLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("detectedFrequency").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(textLayouts)
            }
            textLayouts.forEach { assertFalse("Frequency clipped for $next", it.hasVisualOverflow) }
        }
    }

    @Test fun staleReadingIsLabeledAndExpirationRemovesTuningStatus() {
        val result = TuningCalculator.calculate(80.5, StandardTuning.strings.first(), .99)
        var state by mutableStateOf(TunerUiState(hasPermission = true, microphoneActive = true,
            result = result, readingStatus = ReadingStatus.STALE))
        compose.setContent { MyTuneTheme { TunerScreen(state, {}, {}) } }
        compose.onNodeWithText("Aperte a corda · Sem atualização").assertExists()
        compose.runOnIdle { state = state.copy(result = null, readingStatus = ReadingStatus.ABSENT) }
        compose.onNodeWithText("Aguardando áudio").assertExists()
        compose.onNodeWithText("Aperte a corda · Sem atualização").assertDoesNotExist()
        compose.onNodeWithText("CENTS").assertExists()
    }

    @Test fun eightyHzRetainsTightenGuidanceDuringBriefSilence() {
        val result = TuningCalculator.calculate(80.0, StandardTuning.strings.first(), .99)
        compose.setContent {
            MyTuneTheme { TunerScreen(TunerUiState(hasPermission = true, microphoneActive = true,
                result = result, readingStatus = ReadingStatus.HELD), {}, {}) }
        }
        compose.onNodeWithText("Aperte a corda", substring = true).assertExists()
        compose.onNodeWithText("Nota diferente da corda selecionada", substring = true).assertExists()
        compose.onNodeWithText("Afinada").assertDoesNotExist()
    }
}
