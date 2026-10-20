package com.mytune.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mytune.domain.audio.SignalQuality
import com.mytune.domain.model.*
import com.mytune.domain.tuning.PitchFrequencyCalculator
import com.mytune.presentation.TunerUiState
import com.mytune.presentation.ReadingStatus
import com.mytune.presentation.PreferencesUiState
import com.mytune.presentation.stringVibrationLevel
import com.mytune.ui.components.*
import com.mytune.ui.components.headstock.HeadstockGeometry
import com.mytune.ui.theme.*

@Composable
fun TunerScreen(state: TunerUiState, onSelect: (GuitarStringNote) -> Unit,
    onMicrophoneAction: () -> Unit, modifier: Modifier = Modifier, onModeChange: (TuningMode) -> Unit = {},
    onTuningSelected: suspend (String) -> Boolean = { false },
    onTuningSaved: suspend (String?, String, List<Int>) -> Boolean = { _, _, _ -> false },
    onTuningDeleted: suspend (String) -> Boolean = { false },
    preferencesState: PreferencesUiState = PreferencesUiState(loaded = true),
    onHeadstockChange: (HeadstockType) -> Unit = {}, onThemeChange: (ThemeMode) -> Unit = {},
    onHapticsChange: (Boolean) -> Unit = {}, onAnimationChange: (Boolean) -> Unit = {},
    onHapticSuccess: (() -> Unit)? = null) {
    val preferences = preferencesState.preferences
    var showSettings by rememberSaveable { mutableStateOf(false) }
    TuningHaptics(state, preferencesState.loaded && preferences.haptics && !showSettings, onHapticSuccess)
    if (showSettings) SettingsSheet(preferencesState, { showSettings = false }, onHeadstockChange,
        onThemeChange, onHapticsChange, onAnimationChange)
    var showTunings by rememberSaveable { mutableStateOf(false) }
    if (showTunings) TuningLibrary(state, { showTunings = false }, onTuningSelected, onTuningSaved, onTuningDeleted)
    Surface(modifier.fillMaxSize(), color = Background) {
        BoxWithConstraints(Modifier.safeDrawingPadding().padding(horizontal = 24.dp)) {
            val landscape = maxWidth > maxHeight
            // Keep the three 58 dp selectors at least 59 dp apart; short screens scroll.
            val fontScale = LocalDensity.current.fontScale
            val selectorHeight = guitarStringControlHeight(fontScale)
            val minGuitarHeight = HeadstockGeometry.forType(preferences.headstock)
                .minimumHeight(selectorHeight.value).dp
            val guitarHeight = (maxHeight - 630.dp).coerceIn(minGuitarHeight, maxOf(350.dp, minGuitarHeight))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 920.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Header { showSettings = true }
                TuningModeSelector(state.tuningMode, onModeChange)
                if (landscape) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { TunerPanel(state) }
                        Column(Modifier.weight(1f)) { GuitarSection(state, onSelect, minGuitarHeight, { showTunings = true }, preferences) }
                    }
                } else {
                    TunerPanel(state)
                    GuitarSection(state, onSelect, guitarHeight, { showTunings = true }, preferences)
                }
                AudioFooter(state, onMicrophoneAction)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
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
        TextButton(onClick = onSettings, modifier = Modifier.testTag("openSettings")) { Text("Ajustes") }
    }
}

@Composable
private fun TunerPanel(state: TunerUiState) {
    val readingAlpha by animateFloatAsState(if (state.readingStatus == ReadingStatus.STALE) .95f else 1f, label = "readingAlpha")
    Column(Modifier.fillMaxWidth().testTag("tunerPanel")
        .background(Brush.verticalGradient(listOf(Panel, Background)), RoundedCornerShape(24.dp))
        .border(1.dp, Muted.copy(alpha = .12f), RoundedCornerShape(24.dp))
        .padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(with(LocalDensity.current) { 20.sp.toDp() }).testTag("tunerTargetRow"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AFINADOR", fontSize = 10.sp, letterSpacing = 2.sp, color = Muted,
                style = TextStyle(lineHeight = 16.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
            Text(state.selectedString?.let { "${it.number}ª CORDA · ${it.label}" } ?: "AGUARDANDO CORDA",
                fontSize = 10.sp, color = Orange, letterSpacing = 1.sp, maxLines = 1, softWrap = false,
                style = TextStyle(lineHeight = 16.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = readingAlpha }) {
            NoteDisplay(state)
            TuningIndicator(state.result, state.hasTuningGuidance)
        }
        Spacer(Modifier.height(17.dp))
        val status = when {
            !state.tuningsLoaded -> "Carregando afinações"
            state.tuningOperationPending -> "Salvando afinação"
            !state.hasPermission -> "Microfone necessário"
            state.error != null -> "Captação interrompida"
            !state.microphoneActive -> "Preparando microfone"
            state.tuningMode == TuningMode.AUTOMATIC && state.automaticSelectionStatus == AutomaticSelectionStatus.CONFIRMING ->
                "Identificando a corda"
            state.tuningMode == TuningMode.AUTOMATIC && state.automaticSelectionStatus == AutomaticSelectionStatus.UNCERTAIN ->
                if (state.stringClassification?.status == StringClassificationStatus.NO_MATCH) "Sem correspondência · Use o modo Manual"
                else "Toque uma corda por vez"
            state.readingStatus == ReadingStatus.STALE -> "Última leitura · Sem atualização"
            state.tuningMode == TuningMode.AUTOMATIC && !state.hasTuningGuidance ->
                if (state.result == null) "Toque uma corda" else "Última leitura · Aguardando corda"
            state.result?.status == TuningStatus.IN_TUNE -> "Afinada"
            state.result?.status == TuningStatus.WRONG_NOTE ->
                (if (state.result.cents < 0) "Aperte a corda" else "Afrouxe a corda") + " · Nota diferente da corda selecionada"
            state.result?.status == TuningStatus.FLAT -> "Aperte a corda"
            state.result?.status == TuningStatus.SHARP -> "Afrouxe a corda"
            state.signalQuality == SignalQuality.UNRELIABLE -> "Toque uma corda por vez"
            else -> "Aguardando áudio"
        }
        val messages = listOf("Carregando afinações", "Salvando afinação", "Microfone necessário", "Captação interrompida", "Preparando microfone",
            "Afinada", "Aperte a corda · Nota diferente da corda selecionada",
            "Afrouxe a corda · Nota diferente da corda selecionada", "Aperte a corda", "Afrouxe a corda",
            "Toque uma corda por vez", "Aguardando áudio", "Última leitura · Sem atualização",
            "Identificando a corda", "Sem correspondência · Use o modo Manual", "Toque uma corda",
            "Última leitura · Aguardando corda")
        val staleSuffix = " · Sem atualização"
        StableText(status,
            messages + messages.map { it + staleSuffix },
            TextStyle(color = if (!state.hasTuningGuidance) Muted else
                if (state.result?.status == TuningStatus.IN_TUNE) Green else if (state.result != null) Orange else Muted,
                fontSize = 13.sp, fontWeight = FontWeight.Medium),
            Modifier.fillMaxWidth().testTag("tuningStatus"))
    }
}

@Composable
private fun GuitarSection(state: TunerUiState, onSelect: (GuitarStringNote) -> Unit, height: Dp, onTunings: () -> Unit, preferences: AppPreferences) {
    Column(Modifier.testTag("guitarSection")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTunings, enabled = state.tuningsLoaded && !state.tuningOperationPending,
                modifier = Modifier.weight(1f).testTag("tuningSelector"), contentPadding = PaddingValues(end = 8.dp)) {
                Text("Afinação: ${state.activeTuning.name}", Modifier.fillMaxWidth(), fontSize = 12.sp,
                    maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text("A4  ${PitchFrequencyCalculator.A4_HZ.toInt()} Hz", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(16.dp))
        GuitarView(state.selectedString, onSelect, Modifier.height(height),
            selectionAlpha = if (state.tuningMode == TuningMode.AUTOMATIC && !state.hasTuningGuidance) .55f else 1f,
            strings = state.activeTuning.strings, headstock = preferences.headstock,
            vibrationLevel = state.stringVibrationLevel(preferences.stringAnimation), animationsEnabled = preferences.stringAnimation)
        StableText(if (state.tuningMode == TuningMode.MANUAL) "Toque para selecionar a corda" else "Toque para voltar ao modo Manual",
            listOf("Toque para selecionar a corda", "Toque para voltar ao modo Manual"),
            TextStyle(color = Muted, fontSize = 10.sp),
            Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun AudioFooter(state: TunerUiState, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        AudioLevelIndicator(state.microphoneActive, state.audioLevel)
        (state.tuningStorageError ?: state.tuningRecoveryNotice)?.let {
            Text(it, color = Orange, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (!state.hasPermission || state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(state.error ?: "Permita o microfone para ouvir sua guitarra.", fontSize = 12.sp, color = Muted)
            TextButton(onClick = onAction, contentPadding = PaddingValues(top = 6.dp, end = 8.dp)) {
                Text(if (state.hasPermission) "Tentar novamente" else "Permitir microfone", fontSize = 12.sp)
            }
        } else {
            Text("Toque uma corda e deixe a nota soar.", Modifier.padding(top = 7.dp), color = Muted, fontSize = 10.sp)
        }
    }
}
