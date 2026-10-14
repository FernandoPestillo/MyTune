package com.mytune.domain.audio

import kotlin.math.*

/** YIN difference + cumulative mean normalization + interpolated first trough.
 * One instance per analysis stream; scratch storage is reused, not thread safe.
 */
class YinPitchDetector(
    private val minFrequency: Double = 55.0,
    private val maxFrequency: Double = 1100.0,
    private val threshold: Double = 0.15,
    private val minimumConfidence: Double = 0.90,
    private val minimumRms: Double = 0.0025,
) : PitchDetector {
    private var difference = DoubleArray(0)

    override fun detect(samples: FloatArray, sampleRate: Int): PitchDetection {
        require(sampleRate > 0)
        require(samples.size >= 2 * ceil(sampleRate / minFrequency).toInt() + 2) {
            "Analysis window is too short for the minimum frequency"
        }
        var mean = 0.0
        for (sample in samples) {
            if (!sample.isFinite()) return PitchDetection(quality = SignalQuality.UNRELIABLE)
            mean += sample
        }
        mean /= samples.size
        var energy = 0.0
        for (sample in samples) energy += (sample - mean).pow(2)
        val rms = sqrt(energy / samples.size)
        if (rms < minimumRms) return PitchDetection(rms = rms)

        val maxLag = ceil(sampleRate / minFrequency).toInt()
        val minLag = max(2, floor(sampleRate / maxFrequency).toInt())
        if (difference.size != maxLag + 2) difference = DoubleArray(maxLag + 2)
        val comparisonSize = samples.size - maxLag - 1
        difference[0] = 1.0
        var runningSum = 0.0
        for (lag in 1..maxLag + 1) {
            var sum = 0.0
            for (index in 0 until comparisonSize) {
                val delta = (samples[index] - samples[index + lag]).toDouble()
                sum += delta * delta
            }
            runningSum += sum
            difference[lag] = if (runningSum > 0.0) sum * lag / runningSum else 1.0
        }
        var lag = minLag
        while (lag <= maxLag) {
            if (difference[lag] < threshold) {
                while (lag < maxLag && difference[lag + 1] < difference[lag]) lag++
                val confidence = (1.0 - difference[lag]).coerceIn(0.0, 1.0)
                if (confidence < minimumConfidence) break
                val left = difference[lag - 1]
                val center = difference[lag]
                val right = difference[lag + 1]
                val denominator = left - 2 * center + right
                val offset = if (abs(denominator) > 1e-12)
                    (0.5 * (left - right) / denominator).coerceIn(-1.0, 1.0) else 0.0
                val frequency = sampleRate / (lag + offset)
                if (frequency in minFrequency..maxFrequency) {
                    return PitchDetection(frequency, confidence, rms, SignalQuality.PITCHED)
                }
                break
            }
            lag++
        }
        return PitchDetection(rms = rms, quality = SignalQuality.UNRELIABLE)
    }
}
