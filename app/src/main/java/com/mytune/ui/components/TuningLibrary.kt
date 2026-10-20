package com.mytune.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mytune.domain.model.GuitarTuning
import com.mytune.domain.tuning.*
import com.mytune.presentation.TunerUiState
import com.mytune.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningLibrary(state: TunerUiState, onDismiss: () -> Unit,
    onSelect: suspend (String) -> Boolean,
    onSave: suspend (String?, String, List<Int>) -> Boolean,
    onDelete: suspend (String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var editorBaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val base = state.availableTunings.firstOrNull { it.id == editorBaseId }
    if (base != null) {
        TuningEditor(base, editingId, state.tuningOperationPending, state.tuningStorageError,
            onCancel = { editorBaseId = null; editingId = null },
            onSave = { name, notes -> scope.launch {
                if (onSave(editingId, name, notes)) { editorBaseId = null; editingId = null }
            } })
    } else {
        ModalBottomSheet(onDismissRequest = { if (!state.tuningOperationPending) onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
            LazyColumn(Modifier.fillMaxWidth().testTag("tuningLibrary"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Afinações", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        TextButton(onClick = onDismiss, enabled = !state.tuningOperationPending) { Text("Fechar") }
                    }
                    Button(onClick = { editorBaseId = state.activeTuning.id; editingId = null },
                        enabled = !state.tuningOperationPending, modifier = Modifier.testTag("newTuning")) { Text("Nova afinação") }
                    state.tuningStorageError?.let { Text(it, color = Orange, modifier = Modifier.testTag("tuningStorageError")) }
                    state.tuningRecoveryNotice?.let { Text(it, color = Muted, fontSize = 12.sp) }
                    Text("Predefinidas", Modifier.padding(vertical = 12.dp), color = Muted)
                }
                items(state.availableTunings.filterNot { it.isCustom }, key = { it.id }) { tuning ->
                    TuningItem(tuning, state.activeTuning.id == tuning.id, !state.tuningOperationPending,
                        onSelect = { scope.launch { if (onSelect(tuning.id)) onDismiss() } },
                        onCopy = { editorBaseId = tuning.id; editingId = null })
                }
                item { Text("Personalizadas", Modifier.padding(vertical = 12.dp), color = Muted) }
                if (state.availableTunings.none { it.isCustom }) item {
                    Text("Crie uma afinação a partir de qualquer preset.", color = Muted, fontSize = 12.sp)
                }
                items(state.availableTunings.filter { it.isCustom }, key = { it.id }) { tuning ->
                    TuningItem(tuning, state.activeTuning.id == tuning.id, !state.tuningOperationPending,
                        onSelect = { scope.launch { if (onSelect(tuning.id)) onDismiss() } },
                        onCopy = { editorBaseId = tuning.id; editingId = null },
                        onEdit = { editorBaseId = tuning.id; editingId = tuning.id }, onDelete = { deleteId = tuning.id })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    state.availableTunings.firstOrNull { it.id == deleteId && it.isCustom }?.let { tuning ->
        AlertDialog(onDismissRequest = { if (!state.tuningOperationPending) deleteId = null },
            title = { Text("Excluir afinação?") },
            text = { Text("Excluir “${tuning.name}”? Se ela estiver ativa, E Standard será selecionada.") },
            confirmButton = { TextButton(onClick = { scope.launch { if (onDelete(tuning.id)) { deleteId = null; onDismiss() } } },
                enabled = !state.tuningOperationPending, modifier = Modifier.testTag("confirmDeleteTuning")) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleteId = null }, enabled = !state.tuningOperationPending) { Text("Cancelar") } })
    }
}

@Composable
private fun TuningItem(tuning: GuitarTuning, selected: Boolean, enabled: Boolean, onSelect: () -> Unit,
    onCopy: () -> Unit, onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)
        .border(1.dp, if (selected) Orange else Muted.copy(alpha = .2f), RoundedCornerShape(12.dp))) {
        Column(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .testTag("tuning_${tuning.id}").padding(12.dp)) {
            Text(tuning.name, color = if (selected) Orange else Foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tuning.strings.sortedByDescending { it.number }.joinToString(" · ") { it.label },
                color = Muted, fontSize = 12.sp)
            if (selected) Text("Afinação ativa", color = Orange, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCopy, enabled = enabled, modifier = Modifier.testTag("copy_${tuning.id}")) { Text("Criar cópia") }
            onEdit?.let { TextButton(onClick = it, enabled = enabled, modifier = Modifier.testTag("edit_${tuning.id}")) { Text("Editar") } }
            onDelete?.let { TextButton(onClick = it, enabled = enabled, modifier = Modifier.testTag("delete_${tuning.id}")) { Text("Excluir") } }
        }
    }
}

@Composable
private fun TuningEditor(base: GuitarTuning, editingId: String?, busy: Boolean, storageError: String?,
    onCancel: () -> Unit, onSave: (String, List<Int>) -> Unit) {
    var name by rememberSaveable(base.id, editingId) { mutableStateOf(if (editingId == null) "${base.name} (cópia)" else base.name) }
    var pitches by rememberSaveable(base.id, editingId) { mutableStateOf(base.strings.sortedByDescending { it.number }.map { it.midi }.toIntArray()) }
    val validation = TuningValidator.error(TuningValidator.custom(editingId ?: "custom-draft", name, pitches.toList()))
    Dialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 600.dp).fillMaxWidth()
            .fillMaxHeight(.9f).imePadding(),
            shape = RoundedCornerShape(20.dp), color = Panel) {
            Column(Modifier.padding(20.dp)) {
                Text(if (editingId == null) "Nova afinação" else "Editar afinação", fontSize = 22.sp)
                LazyColumn(Modifier.weight(1f, fill = false).testTag("tuningEditor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(name, { name = it }, label = { Text("Nome da afinação") },
                            singleLine = true, enabled = !busy, isError = name.isBlank() || name.length > TuningValidator.MAX_NAME_LENGTH,
                            modifier = Modifier.fillMaxWidth().testTag("tuningName"))
                        Text("Faixa suportada: B1 a E5 · A4 = ${PitchFrequencyCalculator.A4_HZ.toInt()} Hz", color = Muted, fontSize = 12.sp)
                    }
                    items(6) { index ->
                        val number = 6 - index
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${number}ª corda", Modifier.weight(1f).padding(top = 12.dp))
                            PitchChoice("edit_note_$number", PitchFrequencyCalculator.name(pitches[index]), !busy,
                                PitchFrequencyCalculator.noteNames.mapIndexed { pitchClass, label ->
                                    val midi = (PitchFrequencyCalculator.octave(pitches[index]) + 1) * 12 + pitchClass
                                    PitchOption(label, midi in TuningValidator.MIN_MIDI..TuningValidator.MAX_MIDI, midi)
                                }) { midi -> pitches = pitches.copyOf().also { it[index] = midi } }
                            PitchChoice("edit_octave_$number", PitchFrequencyCalculator.octave(pitches[index]).toString(), !busy,
                                (1..5).map { octave ->
                                    val midi = (octave + 1) * 12 + Math.floorMod(pitches[index], 12)
                                    PitchOption(octave.toString(), midi in TuningValidator.MIN_MIDI..TuningValidator.MAX_MIDI, midi)
                                }) { midi -> pitches = pitches.copyOf().also { it[index] = midi } }
                        }
                    }
                    item {
                        (validation ?: storageError)?.let { Text(it, color = Orange, modifier = Modifier.testTag("tuningEditorError")) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel, enabled = !busy) { Text("Cancelar") }
                    Button(onClick = { onSave(name, pitches.toList()) }, enabled = !busy && validation == null,
                        modifier = Modifier.testTag("saveTuning")) { Text(if (busy) "Salvando…" else "Salvar") }
                }
            }
        }
    }
}

private data class PitchOption(val label: String, val enabled: Boolean, val midi: Int)
@Composable
private fun PitchChoice(tag: String, value: String, enabled: Boolean, options: List<PitchOption>, onChoose: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.testTag(tag)) { Text(value) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option.label) },
                enabled = option.enabled, modifier = Modifier.testTag("${tag}_${option.label}"),
                onClick = { expanded = false; onChoose(option.midi) }) }
        }
    }
}
