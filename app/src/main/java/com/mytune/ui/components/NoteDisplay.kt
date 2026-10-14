package com.mytune.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytune.domain.model.TuningStatus
import com.mytune.presentation.TunerUiState
import com.mytune.ui.theme.*
import java.util.Locale

fun hz(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value)

@Composable
fun NoteDisplay(state: TunerUiState) {
    // Stack only when the available width or accessibility font scale requires it.
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("noteDisplay")) {
        if (maxWidth < 300.dp * LocalDensity.current.fontScale) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DetectedNote(state)
                FrequencyDisplay(state, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(.42f)) { DetectedNote(state) }
                FrequencyDisplay(state, Modifier.weight(.58f))
            }
        }
    }
}

@Composable
private fun DetectedNote(state: TunerUiState) {
    val result = state.result
    // A single line reserves the same height for silence, accidentals and every octave.
    Row(verticalAlignment = Alignment.CenterVertically) {
        StableText(result?.note?.name ?: "—",
            listOf("—", "C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"),
            TextStyle(fontSize = 82.sp, fontWeight = FontWeight.Light,
                color = if (result?.status == TuningStatus.IN_TUNE) Green else Foreground,
                letterSpacing = (-4).sp, lineHeight = 100.sp), maxLines = 1, softWrap = false)
        StableText(result?.note?.octave?.toString() ?: " ", (0..9).map { it.toString() },
            TextStyle(color = Muted, fontSize = 28.sp), Modifier.padding(start = 5.dp, top = 32.dp),
            maxLines = 1, softWrap = false)
    }
}

@Composable
private fun FrequencyDisplay(state: TunerUiState, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text("FREQUÊNCIA DETECTADA", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp,
            maxLines = 1, softWrap = false)
        Spacer(Modifier.height(6.dp))
        Text(state.result?.let { "${hz(it.frequency)} Hz" } ?: "— Hz", Modifier.fillMaxWidth().testTag("detectedFrequency"),
            fontSize = 23.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
            style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false)
        Spacer(Modifier.height(7.dp))
        Text("Alvo ${state.selectedString.label} · ${hz(state.selectedString.frequency)} Hz", color = Muted,
            fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false)
    }
}
