package com.mytune.domain.model

enum class TuningStatus { FLAT, SHARP, IN_TUNE, WRONG_NOTE }

data class TuningResult(
    val frequency: Double,
    val note: DetectedNote,
    val cents: Double,
    val status: TuningStatus,
    val confidence: Double,
)
