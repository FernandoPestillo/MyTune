package com.mytune.domain.tuning

import com.mytune.domain.model.*

object TuningValidator {
    // Safely inside YIN's 55–1100 Hz, with space for the ±150-cent classification band.
    const val MIN_MIDI = 35 // B1 = 61.735 Hz
    const val MAX_MIDI = 76 // E5 = 659.255 Hz; higher harmonic-rich notes failed validation
    const val MAX_NAME_LENGTH = 60
    fun error(tuning: GuitarTuning): String? = when {
        !tuning.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) -> "Identificador de afinação inválido."
        tuning.name.isBlank() -> "Informe um nome para a afinação."
        tuning.name.length > MAX_NAME_LENGTH || tuning.name.any { it.isISOControl() } -> "Use um nome de até $MAX_NAME_LENGTH caracteres, em uma linha."
        tuning.strings.size != 6 || tuning.strings.map { it.number }.toSet() != (1..6).toSet() -> "A afinação deve conter exatamente as seis cordas, de 6 a 1."
        tuning.strings.any { it.midi !in MIN_MIDI..MAX_MIDI } -> "Escolha notas entre B1 e E5, faixa suportada pelo afinador."
        tuning.strings.any { it.name != PitchFrequencyCalculator.name(it.midi) && it.name != PitchFrequencyCalculator.name(it.midi, true) } -> "A nota e a altura musical não correspondem."
        !tuning.isCustom && TuningPresets.find(tuning.id) != tuning -> "Afinações predefinidas não podem ser modificadas."
        tuning.isCustom && TuningPresets.find(tuning.id) != null -> "Crie uma cópia para modificar uma afinação predefinida."
        else -> null
    }
    fun custom(id: String, name: String, midi: List<Int>): GuitarTuning = GuitarTuning(id, name.trim(),
        midi.mapIndexed { i, pitch -> GuitarStringNote(6 - i, pitch) }, true)
}
