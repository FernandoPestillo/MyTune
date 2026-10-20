package com.mytune.domain.audio

import kotlin.math.*

/** Median of three and a short EMA in cents; large note changes reset rather than glide through zero. */
class PitchSmoother(private val alpha: Double = .55) {
    init { require(alpha > 0 && alpha <= 1) }
    private val history = DoubleArray(3)
    private val sorted = DoubleArray(3)
    private var count = 0
    private var position = 0
    private var previous: Double? = null

    fun reset() { count = 0; position = 0; previous = null }

    fun smooth(frequency: Double): Double {
        previous?.let {
            if (TuningCalculator.note(frequency).midi != TuningCalculator.note(it).midi ||
                abs(TuningCalculator.cents(frequency, it)) > 80) reset()
        }
        history[position] = 1200 * log2(frequency)
        position = (position + 1) % history.size
        count = (count + 1).coerceAtMost(history.size)
        history.copyInto(sorted, endIndex = count)
        java.util.Arrays.sort(sorted, 0, count)
        val median = sorted[count / 2]
        val smoothed = previous?.let { 1200 * log2(it) + alpha * (median - 1200 * log2(it)) } ?: median
        val result = 2.0.pow(smoothed / 1200)
        previous = result
        return result
    }
}
