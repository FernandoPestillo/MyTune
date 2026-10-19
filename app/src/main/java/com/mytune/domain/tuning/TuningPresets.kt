package com.mytune.domain.tuning

import com.mytune.domain.model.*

object TuningPresets {
    private fun preset(id: String, name: String, vararg midi: Int, flats: Boolean = false) = GuitarTuning(
        id, name, midi.mapIndexed { index, pitch -> GuitarStringNote(6 - index, pitch, PitchFrequencyCalculator.name(pitch, flats)) }, false)
    val standard = preset("e-standard", "E Standard", 40, 45, 50, 55, 59, 64)
    val all = listOf(
        standard,
        preset("drop-d", "Drop D", 38, 45, 50, 55, 59, 64),
        preset("eb-standard", "Eb Standard", 39, 44, 49, 54, 58, 63, flats = true),
        preset("d-standard", "D Standard", 38, 43, 48, 53, 57, 62),
        preset("drop-c", "Drop C", 36, 43, 48, 53, 57, 62),
        preset("open-d", "Open D", 38, 45, 50, 54, 57, 62),
        preset("open-g", "Open G", 38, 43, 50, 55, 59, 62),
        preset("open-e", "Open E", 40, 47, 52, 56, 59, 64),
        preset("open-a", "Open A", 40, 45, 52, 57, 61, 64),
    )
    fun find(id: String): GuitarTuning? = all.firstOrNull { it.id == id }
}
