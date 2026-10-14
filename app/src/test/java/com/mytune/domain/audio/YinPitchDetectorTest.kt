package com.mytune.domain.audio

import com.mytune.domain.model.StandardTuning
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class YinPitchDetectorTest {
    private val detector = YinPitchDetector()
    private fun tone(frequency: Double, rate: Int = 44_100, phase: Double = 0.0,
        harmonics: Boolean = false, noise: Double = 0.0): FloatArray {
        val random = Random(81)
        return FloatArray(4096) { index ->
            val angle = 2 * PI * frequency * index / rate + phase
            (if (harmonics) .22 * sin(angle) + .55 * sin(2 * angle) + .18 * sin(3 * angle)
                else .6 * sin(angle)).plus(noise * random.nextDouble(-1.0, 1.0)).toFloat()
        }
    }

    @Test fun `all six rounded reference tones at both sample rates are accurate within one cent`() {
        for (rate in listOf(44_100, 48_000)) for (frequency in listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)) {
            val result = detector.detect(tone(frequency, rate), rate)
            assertEquals("$frequency at $rate", SignalQuality.PITCHED, result.quality)
            assertTrue(result.confidence > .98)
            assertTrue("${result.frequency} vs $frequency", abs(TuningCalculator.cents(result.frequency!!, frequency)) < 1.0)
        }
    }

    @Test fun `strong second harmonic does not cause octave errors`() {
        for (rate in listOf(44_100, 48_000)) for (string in StandardTuning.strings) {
            val result = detector.detect(tone(string.frequency, rate, harmonics = true), rate)
            assertEquals(SignalQuality.PITCHED, result.quality)
            assertTrue(abs(TuningCalculator.cents(result.frequency!!, string.frequency)) < 1.5)
        }
    }

    @Test fun `successive windows retain stable pitch across phase and mild noise`() {
        for (rate in listOf(44_100, 48_000)) for (string in StandardTuning.strings) {
            val frequencies = (0 until 12).map { frame ->
                val phase = 2 * PI * string.frequency * frame * 2048 / rate
                detector.detect(tone(string.frequency, rate, phase, harmonics = true, noise = .015), rate).frequency!!
            }
            assertTrue(abs(TuningCalculator.cents(frequencies.max(), frequencies.min())) < 2)
        }
    }

    @Test fun `silence DC offset and subthreshold audio have no pitch`() {
        for (samples in listOf(FloatArray(4096), FloatArray(4096) { .4f },
            FloatArray(4096) { (.0001 * sin(2 * PI * 110 * it / 44_100)).toFloat() })) {
            val result = detector.detect(samples, 44_100)
            assertNull(result.frequency)
            assertEquals(SignalQuality.SILENCE, result.quality)
        }
    }

    @Test fun `broadband noise is rejected for insufficient confidence`() {
        val random = Random(7)
        repeat(8) {
            val result = detector.detect(FloatArray(4096) { random.nextDouble(-.5, .5).toFloat() }, 44_100)
            assertNull(result.frequency)
            assertEquals(SignalQuality.UNRELIABLE, result.quality)
        }
    }

    @Test fun `strict confidence requirement rejects an otherwise periodic noisy signal`() {
        val strict = YinPitchDetector(minimumConfidence = .999999)
        val result = strict.detect(tone(110.0, noise = .03), 44_100)
        assertEquals(SignalQuality.UNRELIABLE, result.quality)
        assertNull(result.frequency)
    }

    @Test fun `DC offset does not alter pitch`() {
        val samples = tone(82.406889)
        for (i in samples.indices) samples[i] += .2f
        assertEquals(82.406889, detector.detect(samples, 44_100).frequency!!, .03)
    }

    @Test fun `actual other notes and octaves remain detectable`() {
        for (frequency in listOf(65.406, 164.814, 440.0, 659.255, 987.767)) {
            assertTrue(abs(TuningCalculator.cents(detector.detect(tone(frequency), 44_100).frequency!!, frequency)) < 2)
        }
    }

    @Test fun `non finite input is rejected`() {
        assertEquals(SignalQuality.UNRELIABLE, detector.detect(FloatArray(4096) { Float.NaN }, 44_100).quality)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `too short windows fail explicitly`() { detector.detect(FloatArray(128), 44_100) }
}
