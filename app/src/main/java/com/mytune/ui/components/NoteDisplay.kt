package com.mytune.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val result = state.result
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(result?.note?.name ?: "—", fontSize = 82.sp, fontWeight = FontWeight.Light,
                color = if (result?.status == TuningStatus.IN_TUNE) Green else Foreground,
                letterSpacing = (-4).sp)
            Text(result?.note?.octave?.toString() ?: "", modifier = Modifier.padding(start = 5.dp, top = 32.dp),
                color = Muted, fontSize = 28.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("FREQUÊNCIA DETECTADA", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(result?.let { "${hz(it.frequency)} Hz" } ?: "— Hz", fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Text("Alvo ${state.selectedString.label} · ${hz(state.selectedString.frequency)} Hz", color = Muted, fontSize = 11.sp)
        }
    }
}
