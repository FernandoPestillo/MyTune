package com.mytune.domain.audio

/** Algorithm scores describe periodicity, not calibrated probabilities. */
data class YinConfig(
    val threshold: Double = .15,
    val minFrequencyHz: Double = 55.0,
    val maxFrequencyHz: Double = 1100.0,
    val minConfidence: Double = .90,
    val minimumRms: Double = .0025,
    // Short moving average: first zero at ~5 kHz; reduces broadband-noise troughs.
    // Set to null for an unfiltered comparison. Does not remove the E2 fundamental.
    val lowPassFirstZeroHz: Double? = 5000.0,
    val harmonicCheckMinimumResidual: Double = .005,
    val harmonicImprovementRatio: Double = .2,
    val maximumPeriodMultiple: Int = 3,
) {
    init {
        require(threshold > 0 && threshold < 1 && minConfidence in 0.0..1.0)
        require(minFrequencyHz > 0 && maxFrequencyHz > minFrequencyHz && maxFrequencyHz.isFinite())
        require(minimumRms > 0 && minimumRms.isFinite())
        require(lowPassFirstZeroHz == null || (lowPassFirstZeroHz > 0 && lowPassFirstZeroHz.isFinite()))
        require(harmonicCheckMinimumResidual > 0 && harmonicCheckMinimumResidual < 1)
        require(harmonicImprovementRatio > 0 && harmonicImprovementRatio < 1 && maximumPeriodMultiple in 1..4)
    }
}

data class AudioProcessingConfig(val windowSize: Int = 4096, val hopSize: Int = 2048) {
    init { require(windowSize >= 4 && hopSize in 1..windowSize) }
}
