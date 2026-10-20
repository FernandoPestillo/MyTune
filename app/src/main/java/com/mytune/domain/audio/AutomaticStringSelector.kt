package com.mytune.domain.audio

import com.mytune.domain.model.*
import kotlin.math.abs

data class AutomaticSelectionConfig(
    val confirmationMs: Long = 120,
    val minimumValidReadings: Int = 2,
    val minimumEvidenceIntervalMs: Long = 40,
    val maximumEvidenceGapMs: Long = 200,
    val candidateSpreadCents: Double = 60.0,
    val octaveConfirmationMs: Long = 180,
) {
    init {
        require(confirmationMs > 0 && minimumValidReadings >= 2)
        require(minimumEvidenceIntervalMs > 0 && maximumEvidenceGapMs >= minimumEvidenceIntervalMs)
        require(candidateSpreadCents > 0 && candidateSpreadCents.isFinite())
        require(octaveConfirmationMs >= confirmationMs)
    }
}

data class AutomaticSelectionDecision(
    val status: AutomaticSelectionStatus,
    val acceptedString: GuitarStringNote? = null,
)

/** Candidate hysteresis only. The existing presentation policy owns reading/target retention.
 * The active target is supplied by the owner; no second active selection is maintained here.
 * Times must come from the same monotonic clock as the rest of the pipeline.
 */
class AutomaticStringSelector(private val config: AutomaticSelectionConfig = AutomaticSelectionConfig()) {
    private var pending: GuitarStringNote? = null
    private var anchorCents = 0.0
    private var firstMs = 0L
    private var lastEvidenceMs = 0L
    private var lastSeenMs = 0L
    private var count = 0

    fun reset() { pending = null; count = 0 }

    /** Expires unconfirmed evidence, not a held reading. No audio or wall-clock dependency. */
    fun advance(nowMs: Long): Boolean {
        if (pending != null && (nowMs < lastSeenMs || nowMs - lastSeenMs > config.maximumEvidenceGapMs)) reset()
        return pending != null
    }

    fun accept(classification: StringDetectionResult, active: GuitarStringNote?, nowMs: Long): AutomaticSelectionDecision {
        advance(nowMs)
        val candidate = classification.candidate
        val cents = classification.cents
        if (classification.status != StringClassificationStatus.MATCH || candidate == null ||
            cents == null || !cents.isFinite() || !classification.confidence.isFinite() || classification.confidence !in 0.0..1.0) {
            reset()
            return AutomaticSelectionDecision(AutomaticSelectionStatus.UNCERTAIN)
        }
        if (candidate == active) {
            reset()
            return AutomaticSelectionDecision(AutomaticSelectionStatus.TRACKING, active)
        }
        if (candidate != pending || abs(cents - anchorCents) > config.candidateSpreadCents) {
            pending = candidate
            anchorCents = cents
            firstMs = nowMs
            lastEvidenceMs = nowMs
            count = 1
        } else if (nowMs - lastEvidenceMs >= config.minimumEvidenceIntervalMs) {
            count++
            lastEvidenceMs = nowMs
        }
        lastSeenMs = nowMs
        val interval = active?.let { abs(TuningCalculator.cents(candidate.frequency, it.frequency)) }
        val octaveSuspect = interval != null && (abs(interval - 1200) <= 80 || abs(interval - 2400) <= 80)
        val requiredMs = if (octaveSuspect) config.octaveConfirmationMs else config.confirmationMs
        if (count >= config.minimumValidReadings && lastEvidenceMs - firstMs >= requiredMs) {
            reset()
            return AutomaticSelectionDecision(AutomaticSelectionStatus.TRACKING, candidate)
        }
        return AutomaticSelectionDecision(AutomaticSelectionStatus.CONFIRMING)
    }
}
