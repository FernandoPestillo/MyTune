package com.mytune.domain.model

data class DetectedNote(val name: String, val octave: Int, val midi: Int) {
    val label: String get() = "$name$octave"
}
