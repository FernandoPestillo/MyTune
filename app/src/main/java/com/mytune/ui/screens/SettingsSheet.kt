package com.mytune.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mytune.domain.model.*
import com.mytune.presentation.PreferencesUiState
import com.mytune.ui.components.HeadstockPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(state: PreferencesUiState, onDismiss: () -> Unit,
    onHeadstock: (HeadstockType) -> Unit, onTheme: (ThemeMode) -> Unit,
    onHaptics: (Boolean) -> Unit, onAnimation: (Boolean) -> Unit) {
    val enabled = state.loaded && !state.saving
    val preferences = state.preferences
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().testTag("settingsSheet"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Configurações", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                }
                if (!state.loaded) Text("Carregando preferências…")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("preferencesError")) }
                Text("Modelo de guitarra", style = MaterialTheme.typography.titleMedium)
            }
            HeadstockType.entries.forEach { type -> item {
                OutlinedCard(Modifier.fillMaxWidth().selectable(preferences.headstock == type, enabled,
                    Role.RadioButton, onClick = { onHeadstock(type) }).testTag("headstock_${type.name}")) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeadstockPreview(type, Modifier.width(100.dp).height(140.dp))
                        RadioButton(preferences.headstock == type, onClick = null, enabled = enabled)
                        Text(type.title, Modifier.weight(1f).padding(start = 8.dp))
                    }
                }
            } }
            item {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(preferences.theme == mode,
                        enabled, Role.RadioButton, onClick = { onTheme(mode) }).testTag("theme_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(preferences.theme == mode, onClick = null, enabled = enabled)
                        Text(mode.title, Modifier.padding(start = 12.dp))
                    }
                }
            }
            item { PreferenceSwitch("Feedback tátil", "Um toque curto ao atingir a afinação.", preferences.haptics,
                enabled, "hapticsSwitch", onHaptics) }
            item { PreferenceSwitch("Animação das cordas", "Vibração sutil durante leituras válidas. Respeita a redução de movimento do sistema.",
                preferences.stringAnimation, enabled, "animationSwitch", onAnimation) }
            item { Text("Preferências salvas neste dispositivo. A afinação selecionada permanece independente.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean,
    tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .testTag(tag).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
