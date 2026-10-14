package com.mytune.domain.audio

import kotlin.math.abs

/** Median of three and a short EMA; large note changes reset rather than glide through zero. */
class PitchSmoother {
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
        history[position] = frequency
        position = (position + 1) % history.size
        count = (count + 1).coerceAtMost(history.size)
        history.copyInto(sorted, endIndex = count)
        java.util.Arrays.sort(sorted, 0, count)
        val median = sorted[count / 2]
        val result = previous?.let { it + 0.55 * (median - it) } ?: median
        previous = result
        return result
    }
}
