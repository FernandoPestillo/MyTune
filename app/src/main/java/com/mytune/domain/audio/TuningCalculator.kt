package com.mytune.domain.audio

import com.mytune.domain.model.*
import kotlin.math.*

object TuningCalculator {
    const val TOLERANCE_CENTS = 5.0
    private val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    fun cents(frequency: Double, target: Double): Double {
        require(frequency.isFinite() && frequency > 0 && target.isFinite() && target > 0)
        return 1200.0 * log2(frequency / target)
    }

    fun note(frequency: Double): DetectedNote {
        require(frequency.isFinite() && frequency > 0)
        val midi = (69 + 12 * log2(frequency / 440.0)).roundToInt()
        return DetectedNote(names[Math.floorMod(midi, 12)], Math.floorDiv(midi, 12) - 1, midi)
    }

    fun calculate(frequency: Double, target: GuitarStringNote, confidence: Double): TuningResult {
        val note = note(frequency)
        val deviation = cents(frequency, target.frequency)
        val status = when {
            note.midi != target.midi -> TuningStatus.WRONG_NOTE
            abs(deviation) <= TOLERANCE_CENTS + 1e-9 -> TuningStatus.IN_TUNE
            deviation < 0 -> TuningStatus.FLAT
            else -> TuningStatus.SHARP
        }
        return TuningResult(frequency, note, deviation, status, confidence)
    }
}
