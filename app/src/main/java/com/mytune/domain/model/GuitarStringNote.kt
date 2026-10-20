package com.mytune.domain.model

import com.mytune.domain.tuning.PitchFrequencyCalculator
import com.mytune.domain.tuning.TuningPresets

data class GuitarStringNote(val number: Int, val midi: Int, val name: String = PitchFrequencyCalculator.name(midi)) {
    val frequency: Double get() = PitchFrequencyCalculator.frequency(midi)
    val label: String get() = "$name${PitchFrequencyCalculator.octave(midi)}"
}

/** Compatibility reference for the existing standard-tuning regression corpus; no duplicate data. */
object StandardTuning {
    val strings: List<GuitarStringNote> get() = TuningPresets.standard.strings
}
