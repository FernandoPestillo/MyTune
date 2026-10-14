package com.mytune.domain.model

import kotlin.math.pow

data class GuitarStringNote(val number: Int, val midi: Int, val name: String) {
    val frequency: Double get() = 440.0 * 2.0.pow((midi - 69) / 12.0)
    val label: String get() = "$name${midi / 12 - 1}"
}

object StandardTuning {
    val strings = listOf(
        GuitarStringNote(6, 40, "E"), GuitarStringNote(5, 45, "A"),
        GuitarStringNote(4, 50, "D"), GuitarStringNote(3, 55, "G"),
        GuitarStringNote(2, 59, "B"), GuitarStringNote(1, 64, "E"),
    )
}
