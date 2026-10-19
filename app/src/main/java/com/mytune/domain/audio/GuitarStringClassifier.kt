package com.mytune.domain.audio

import com.mytune.domain.model.*
import kotlin.math.abs

data class StringClassifierConfig(
    val maxDeviationCents: Double = 150.0,
    val minimumConfidence: Double = .90,
    val minimumRms: Double = .0025,
) {
    init {
        require(maxDeviationCents > 0 && maxDeviationCents.isFinite())
        require(minimumConfidence in 0.0..1.0)
        require(minimumRms > 0 && minimumRms.isFinite())
    }
}

/** Stateless classification of the actual fundamental. No octave folding or target feedback. */
class GuitarStringClassifier(
    strings: List<GuitarStringNote> = StandardTuning.strings,
    private val config: StringClassifierConfig = StringClassifierConfig(),
) {
    private val strings = strings.toList()
    init { require(this.strings.isNotEmpty() && this.strings.map { it.number }.distinct().size == this.strings.size) }

    fun classify(detection: PitchDetection): StringDetectionResult {
        val frequency = detection.frequency
        if (frequency == null || !frequency.isFinite() || frequency <= 0 ||
            detection.quality != SignalQuality.PITCHED ||
            !detection.confidence.isFinite() || detection.confidence !in config.minimumConfidence..1.0 ||
            !detection.rms.isFinite() || detection.rms < config.minimumRms) {
            return StringDetectionResult(StringClassificationStatus.INVALID)
        }
        var nearest: GuitarStringNote? = null
        var nearestCents = 0.0
        var distance = Double.POSITIVE_INFINITY
        var secondDistance = Double.POSITIVE_INFINITY
        for (string in strings) {
            val cents = TuningCalculator.cents(frequency, string.frequency)
            val absolute = abs(cents)
            if (absolute < distance) {
                secondDistance = distance
                distance = absolute
                nearest = string
                nearestCents = cents
            } else if (absolute < secondDistance) secondDistance = absolute
        }
        if (distance > config.maxDeviationCents + 1e-9)
            return StringDetectionResult(StringClassificationStatus.NO_MATCH, confidence = detection.confidence)
        if (secondDistance <= config.maxDeviationCents + 1e-9)
            return StringDetectionResult(StringClassificationStatus.AMBIGUOUS, confidence = detection.confidence)
        return StringDetectionResult(StringClassificationStatus.MATCH, nearest, nearestCents, detection.confidence)
    }
}
