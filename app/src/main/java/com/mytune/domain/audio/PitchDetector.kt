package com.mytune.domain.audio

enum class SignalQuality { SILENCE, UNRELIABLE, PITCHED }

data class PitchDetection(
    val frequency: Double? = null,
    val confidence: Double = 0.0,
    val rms: Double = 0.0,
    val quality: SignalQuality = SignalQuality.SILENCE,
)

fun interface PitchDetector {
    /** Samples are normalized mono PCM. The caller retains ownership of the buffer. */
    fun detect(samples: FloatArray, sampleRate: Int): PitchDetection
}
