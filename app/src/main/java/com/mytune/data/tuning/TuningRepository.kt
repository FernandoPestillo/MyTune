package com.mytune.data.tuning

import androidx.datastore.core.DataStore
import com.mytune.domain.model.GuitarTuning
import com.mytune.domain.tuning.*
import kotlinx.coroutines.flow.Flow

/** DataStore serializes transactions; every transform reads the most recently committed library. */
class TuningRepository(private val store: DataStore<TuningSettings>) {
    val settings: Flow<TuningSettings> = store.data
    suspend fun select(id: String): TuningSettings = store.updateData { current ->
        require(current.available.any { it.id == id }) { "Afinação não encontrada." }
        current.copy(selectedId = id, revision = current.revision + 1)
    }
    suspend fun save(tuning: GuitarTuning): TuningSettings = store.updateData { current ->
        require(tuning.isCustom) { "Crie uma cópia para modificar uma afinação predefinida." }
        require(TuningValidator.error(tuning) == null) { TuningValidator.error(tuning).orEmpty() }
        current.copy(customTunings = current.customTunings.filterNot { it.id == tuning.id } +
            tuning.copy(strings = tuning.strings.sortedByDescending { it.number }.toList()),
            selectedId = if (current.customTunings.none { it.id == tuning.id }) tuning.id else current.selectedId,
            revision = current.revision + 1)
    }
    suspend fun delete(id: String): TuningSettings = store.updateData { current ->
        require(TuningPresets.find(id) == null) { "Afinações predefinidas não podem ser excluídas." }
        require(current.customTunings.any { it.id == id }) { "Afinação não encontrada." }
        current.copy(customTunings = current.customTunings.filterNot { it.id == id },
            selectedId = if (current.selectedId == id) TuningPresets.standard.id else current.selectedId,
            revision = current.revision + 1)
    }
}
