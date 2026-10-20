package com.mytune.domain.model

/** Strings keep physical numbers regardless of pitch/order; validated at boundaries. */
data class GuitarTuning(val id: String, val name: String, val strings: List<GuitarStringNote>, val isCustom: Boolean) {
    fun string(number: Int): GuitarStringNote = strings.first { it.number == number }
}
