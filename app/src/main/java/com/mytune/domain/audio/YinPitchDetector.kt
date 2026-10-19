package com.mytune.domain.audio

import kotlin.math.*

/** Constant-length YIN difference, CMNDF, first qualifying trough and refined period.
 * One instance per stream; scratch arrays are reused and are not thread safe.
 * The selected guitar string is deliberately not an input.
 */
class YinPitchDetector(private val config: YinConfig = YinConfig()) : PitchDetector {
    // Compatibility with the existing strict-confidence test/callers.
    constructor(minimumConfidence: Double) : this(YinConfig(minConfidence = minimumConfidence))
    private var difference = DoubleArray(0)
    private var rawDifference = DoubleArray(0)
    private var filtered = FloatArray(0)
    private var computed = 0
    private var runningSum = 0.0
    private var baseEnergy = 0.0
    private var shiftedEnergy = 0.0

    private fun computeThrough(end: Int, maxLag: Int, comparisonSize: Int) {
        for (lag in computed + 1..min(end, maxLag + 1)) {
            var value = 0.0
            for (index in 0 until comparisonSize) {
                val delta = (filtered[index] - filtered[index + lag]).toDouble()
                value += delta * delta
            }
            val removed = filtered[lag - 1].toDouble()
            val added = filtered[lag + comparisonSize - 1].toDouble()
            shiftedEnergy += added * added - removed * removed
            // Gain-match the delayed span. A decaying envelope must not shorten the period.
            if (shiftedEnergy > 1e-20 && baseEnergy > 1e-20) {
                val gain = sqrt(baseEnergy / shiftedEnergy)
                value = max(0.0, 2 * baseEnergy - (baseEnergy + shiftedEnergy - value) * gain)
            }
            rawDifference[lag] = value
            runningSum += value
            difference[lag] = if (runningSum > 0.0) value * lag / runningSum else 1.0
            computed = lag
        }
    }

    override fun detect(samples: FloatArray, sampleRate: Int): PitchDetection {
        require(sampleRate > 2 * config.maxFrequencyHz)
        val maxLag = ceil(sampleRate / config.minFrequencyHz).toInt()
        val filterWidth = config.lowPassFirstZeroHz?.let { (sampleRate / it).roundToInt().coerceAtLeast(1) } ?: 1
        require(samples.size >= 2 * maxLag + filterWidth + 2) { "Analysis window is too short for the minimum frequency" }
        var mean = 0.0
        for (sample in samples) {
            if (!sample.isFinite()) return PitchDetection(quality = SignalQuality.UNRELIABLE)
            mean += sample
        }
        mean /= samples.size
        var energy = 0.0
        for (sample in samples) { val ac = sample - mean; energy += ac * ac }
        val rms = sqrt(energy / samples.size)
        if (rms < config.minimumRms) return PitchDetection(rms = rms)

        if (filtered.size != samples.size) filtered = FloatArray(samples.size)
        // Only complete FIR spans are used; no padding transient or state between overlapping frames.
        var sum = 0.0
        for (i in 0 until filterWidth) sum += samples[i]
        val size = samples.size - filterWidth + 1
        for (i in 0 until size) {
            filtered[i] = (sum / filterWidth - mean).toFloat()
            if (i + filterWidth < samples.size) sum += samples[i + filterWidth] - samples[i]
        }
        if (difference.size != maxLag + 2) {
            difference = DoubleArray(maxLag + 2)
            rawDifference = DoubleArray(maxLag + 2)
        }
        val comparisonSize = size - maxLag - 1
        val minLag = max(2, floor(sampleRate / config.maxFrequencyHz).toInt())
        difference[0] = 1.0
        runningSum = 0.0
        computed = 0
        baseEnergy = 0.0
        for (i in 0 until comparisonSize) { val v = filtered[i].toDouble(); baseEnergy += v * v }
        shiftedEnergy = baseEnergy
        var lag = minLag
        while (lag <= maxLag) {
            computeThrough(lag + 1, maxLag, comparisonSize)
            if (difference[lag] < config.threshold && difference[lag] <= difference[lag - 1] &&
                difference[lag] < difference[lag + 1]) {
                // A little neighborhood search avoids an early noisy ripple in the same trough.
                val end = min(maxLag, lag + max(2, (lag * .03).roundToInt()))
                computeThrough(end + 1, maxLag, comparisonSize)
                var candidate = lag
                for (i in lag + 1..end) if (difference[i] < difference[candidate]) candidate = i
                if (1.0 - difference[candidate] < config.minConfidence) { lag = end + 1; continue }

                // Test only integer-period alternatives, and require strong signal evidence.
                // Pure tones have tiny residuals and cannot be arbitrarily divided in frequency.
                val first = candidate
                if (difference[first] >= config.harmonicCheckMinimumResidual) {
                    for (multiple in 2..config.maximumPeriodMultiple) {
                        val center = first * multiple
                        val radius = max(2, (center * .03).roundToInt())
                        if (center + radius > maxLag) break
                        computeThrough(center + radius + 1, maxLag, comparisonSize)
                        var alternative = center - radius
                        for (i in center - radius..center + radius)
                            if (difference[i] < difference[alternative]) alternative = i
                        if (difference[alternative] < difference[candidate] * config.harmonicImprovementRatio)
                            candidate = alternative
                    }
                }
                // Interpolate the unnormalized difference to avoid CMNDF's period bias.
                val left = rawDifference[candidate - 1]
                val center = rawDifference[candidate]
                val right = rawDifference[candidate + 1]
                val denominator = left - 2 * center + right
                val offset = if (abs(denominator) > 1e-12)
                    (.5 * (left - right) / denominator).coerceIn(-.5, .5) else 0.0
                val frequency = sampleRate / (candidate + offset)
                if (frequency in config.minFrequencyHz..config.maxFrequencyHz) {
                    return PitchDetection(frequency, (1 - difference[candidate]).coerceIn(0.0, 1.0), rms, SignalQuality.PITCHED)
                }
            }
            lag++
        }
        return PitchDetection(rms = rms, quality = SignalQuality.UNRELIABLE)
    }
}
