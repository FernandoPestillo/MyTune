package com.mytune

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mytune.domain.model.*
import com.mytune.presentation.TunerUiState
import com.mytune.ui.screens.TunerScreen
import com.mytune.ui.theme.MyTuneTheme
import org.junit.*
import org.junit.runner.RunWith

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
        compose.onNodeWithText("Nota diferente da corda selecionada").assertExists()
        compose.onNodeWithText("Afinada").assertDoesNotExist()
    }
}
