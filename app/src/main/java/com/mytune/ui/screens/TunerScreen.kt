package com.mytune.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mytune.domain.audio.SignalQuality
import com.mytune.domain.model.*
import com.mytune.presentation.TunerUiState
import com.mytune.ui.components.*
import com.mytune.ui.theme.*

@Composable
fun TunerScreen(state: TunerUiState, onSelect: (GuitarStringNote) -> Unit,
    onMicrophoneAction: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = Background) {
        BoxWithConstraints(Modifier.safeDrawingPadding().padding(horizontal = 24.dp)) {
            val landscape = maxWidth > maxHeight
            // Keep the three 58 dp selectors at least 59 dp apart; short screens scroll.
            val guitarHeight = (maxHeight - 570.dp).coerceIn(290.dp, 350.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 920.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Header()
                if (landscape) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { TunerPanel(state) }
                        Column(Modifier.weight(1f)) { GuitarSection(state, onSelect, 290.dp) }
                    }
                } else {
                    TunerPanel(state)
                    GuitarSection(state, onSelect, guitarHeight)
                }
                AudioFooter(state, onMicrophoneAction)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(25.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(10, 19, 27, 15, 21).forEach { height ->
                    Box(Modifier.width(3.dp).height(height.dp).background(Orange, RoundedCornerShape(2.dp)))
                }
            }
            Text("mytune", Modifier.padding(start = 10.dp), fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
        }
        Text("GUITAR TUNER", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
    }
}

@Composable
private fun TunerPanel(state: TunerUiState) {
    Column(Modifier.fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Panel, Background)), RoundedCornerShape(24.dp))
        .border(1.dp, Muted.copy(alpha = .12f), RoundedCornerShape(24.dp))
        .padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AFINADOR", fontSize = 10.sp, letterSpacing = 2.sp, color = Muted)
            Text("${state.selectedString.number}ª CORDA · ${state.selectedString.label}", fontSize = 10.sp,
                color = Orange, letterSpacing = 1.sp)
        }
        NoteDisplay(state)
        TuningIndicator(state.result)
        Spacer(Modifier.height(17.dp))
        val status = when {
            !state.hasPermission -> "Microfone necessário"
            state.error != null -> "Captação interrompida"
            !state.microphoneActive -> "Preparando microfone"
            state.result?.status == TuningStatus.IN_TUNE -> "Afinada"
            state.result?.status == TuningStatus.WRONG_NOTE -> "Nota diferente da corda selecionada"
            state.result?.status == TuningStatus.FLAT -> "Aperte a corda"
            state.result?.status == TuningStatus.SHARP -> "Afrouxe a corda"
            state.signalQuality == SignalQuality.UNRELIABLE -> "Toque uma corda por vez"
            else -> "Aguardando áudio"
        }
        Text(status, Modifier.align(Alignment.CenterHorizontally),
            color = if (state.result?.status == TuningStatus.IN_TUNE) Green else if (state.result != null) Orange else Muted,
            fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GuitarSection(state: TunerUiState, onSelect: (GuitarStringNote) -> Unit, height: Dp) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("E STANDARD", color = Foreground, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Text("A4  440 Hz", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(16.dp))
        GuitarView(state.selectedString, onSelect, Modifier.height(height))
        Text("Toque para selecionar a corda", Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun AudioFooter(state: TunerUiState, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        AudioLevelIndicator(state.microphoneActive, state.audioLevel)
        if (!state.hasPermission || state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(state.error ?: "Permita o microfone para ouvir sua guitarra.", fontSize = 12.sp, color = Muted)
            TextButton(onClick = onAction, contentPadding = PaddingValues(top = 6.dp, end = 8.dp)) {
                Text(if (state.hasPermission) "Tentar novamente" else "Permitir microfone", fontSize = 12.sp)
            }
        } else {
            Text("Toque uma corda e deixe a nota soar.", Modifier.padding(top = 7.dp), color = Muted.copy(alpha = .7f), fontSize = 10.sp)
        }
    }
}
