package com.mytune.presentation

import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import kotlin.math.abs

enum class ReadingStatus { ABSENT, ACTIVE, HELD, STALE }

data class DetectionPresentation(
    val result: TuningResult? = null,
    val readingStatus: ReadingStatus = ReadingStatus.ABSENT,
    val lastValidDetectionMs: Long? = null,
)

data class DetectionStabilityConfig(
    val detectionHoldDurationMs: Long = 1200,
    val noSignalTimeoutMs: Long = 1800,
    val activeReadingDurationMs: Long = 100,
    val refreshIntervalMs: Long = 50,
    val enterInTuneCents: Double = 5.0,
    val exitInTuneCents: Double = 7.0,
    val jumpThresholdCents: Double = 80.0,
    val changeConfirmationReadings: Int = 2,
    val minimumConfidence: Double = .90,
    val smoothingAlpha: Double = .55,
) {
    init {
        require(activeReadingDurationMs > 0 && activeReadingDurationMs <= detectionHoldDurationMs)
        require(noSignalTimeoutMs > detectionHoldDurationMs && refreshIntervalMs > 0)
        require(enterInTuneCents > 0 && exitInTuneCents >= enterInTuneCents)
        require(jumpThresholdCents > 0 && changeConfirmationReadings >= 2)
        require(minimumConfidence in 0.0..1.0 && smoothingAlpha > 0 && smoothingAlpha <= 1)
    }
}

/** Single presentation policy. Time is supplied by the owner using a monotonic clock. */
class DetectionStabilizer(private val config: DetectionStabilityConfig = DetectionStabilityConfig()) {
    private val smoother = PitchSmoother(config.smoothingAlpha)
    private var presentation = DetectionPresentation()
    private var acceptedFrequency: Double? = null
    private var candidateFrequency: Double? = null
    private var candidateCount = 0

    fun reset(): DetectionPresentation {
        smoother.reset()
        acceptedFrequency = null
        candidateFrequency = null
        candidateCount = 0
        presentation = DetectionPresentation()
        return presentation
    }

    fun advance(nowMs: Long): DetectionPresentation {
        val last = presentation.lastValidDetectionMs ?: return presentation
        val age = (nowMs - last).coerceAtLeast(0)
        if (age >= config.noSignalTimeoutMs) return reset()
        val status = when {
            age >= config.detectionHoldDurationMs -> ReadingStatus.STALE
            age >= config.activeReadingDurationMs -> ReadingStatus.HELD
            else -> presentation.readingStatus
        }
        presentation = presentation.copy(readingStatus = status)
        return presentation
    }

    fun accept(detection: PitchDetection, target: GuitarStringNote, nowMs: Long): DetectionPresentation {
        advance(nowMs)
        val frequency = detection.frequency
        if (frequency == null || !frequency.isFinite() || frequency <= 0 ||
            detection.quality != SignalQuality.PITCHED || !detection.confidence.isFinite() ||
            detection.confidence < config.minimumConfidence) {
            candidateFrequency = null
            candidateCount = 0
            return hold()
        }
        val note = TuningCalculator.note(frequency)
        val previous = acceptedFrequency
        val changedNote = previous != null && note.midi != TuningCalculator.note(previous).midi
        val jumped = previous != null && abs(TuningCalculator.cents(frequency, previous)) > config.jumpThresholdCents
        if (changedNote || jumped) {
            val candidate = candidateFrequency
            candidateCount = if (candidate != null && TuningCalculator.note(candidate).midi == note.midi &&
                abs(TuningCalculator.cents(frequency, candidate)) <= config.jumpThresholdCents) candidateCount + 1 else 1
            candidateFrequency = frequency
            if (candidateCount < config.changeConfirmationReadings) return hold()
            smoother.reset() // Never average different notes, octaves or confirmed large jumps.
        }
        candidateFrequency = null
        candidateCount = 0
        var result = TuningCalculator.calculate(smoother.smooth(frequency), target, detection.confidence)
        val raw = TuningCalculator.calculate(frequency, target, detection.confidence)
        val tolerance = if (presentation.result?.status == TuningStatus.IN_TUNE && presentation.readingStatus != ReadingStatus.STALE && !changedNote && !jumped)
            config.exitInTuneCents else config.enterInTuneCents
        val tuned = raw.note.midi == target.midi && result.note.midi == target.midi &&
            abs(raw.cents) <= tolerance + 1e-9 && abs(result.cents) <= tolerance + 1e-9
        // Do not let the smoothing delay hide an actual departure from the tuned band.
        if (!tuned && result.status == TuningStatus.IN_TUNE && abs(raw.cents) > tolerance) result = raw
        val status = when {
            raw.note.midi != target.midi -> TuningStatus.WRONG_NOTE
            tuned -> TuningStatus.IN_TUNE
            result.cents < 0 -> TuningStatus.FLAT
            else -> TuningStatus.SHARP
        }
        acceptedFrequency = frequency
        presentation = DetectionPresentation(result.copy(status = status), ReadingStatus.ACTIVE, nowMs)
        return presentation
    }

    private fun hold(): DetectionPresentation {
        if (presentation.readingStatus == ReadingStatus.ACTIVE)
            presentation = presentation.copy(readingStatus = ReadingStatus.HELD)
        return presentation
    }
}
