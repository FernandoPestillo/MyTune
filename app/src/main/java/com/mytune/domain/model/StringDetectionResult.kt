package com.mytune.domain.model

enum class StringClassificationStatus { MATCH, NO_MATCH, AMBIGUOUS, INVALID }
enum class AutomaticSelectionStatus { WAITING, CONFIRMING, TRACKING, UNCERTAIN }

/** Confidence is the detector's periodicity score, never a probability of string identity. */
data class StringDetectionResult(
    val status: StringClassificationStatus,
    val candidate: GuitarStringNote? = null,
    val cents: Double? = null,
    val confidence: Double = 0.0,
)
