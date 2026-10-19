package com.mytune.data.tuning

import com.mytune.domain.model.GuitarTuning
import com.mytune.domain.tuning.TuningPresets

/** Presets are never serialized. Damaged/unknown records are retained for recovery, not erased. */
data class TuningSettings(
    val selectedId: String = TuningPresets.standard.id,
    val customTunings: List<GuitarTuning> = emptyList(),
    val revision: Long = 0,
    val recoveredRecords: List<String> = emptyList(),
    val hasRecoveryWarning: Boolean = false,
) {
    val available: List<GuitarTuning> get() = TuningPresets.all + customTunings
    val active: GuitarTuning get() = available.firstOrNull { it.id == selectedId } ?: TuningPresets.standard
}
