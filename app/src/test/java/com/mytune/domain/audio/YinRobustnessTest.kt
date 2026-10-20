package com.mytune.domain.audio

import com.mytune.domain.model.StandardTuning
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class YinRobustnessTest {
    @Test fun `harmonic decisions follow signal periodicity across phases not a selected string`() {
        for (rate in listOf(44_100, 48_000)) for (string in StandardTuning.strings) for (phase in listOf(.1, 1.4, 2.8)) {
            val detector = YinPitchDetector()
            for ((harmonics, expected) in listOf(
                doubleArrayOf(.08, .75, .025) to string.frequency,
                doubleArrayOf(.12, .08, .65) to string.frequency,
                doubleArrayOf(0.0, .55, .25) to string.frequency,
                // With no fundamental or odd harmonics, the real period is half as long.
                doubleArrayOf(0.0, .7) to string.frequency * 2)) {
                val result = detector.detect(PrecisionReferenceTest.signal(string.frequency, rate,
                    amplitudes = harmonics, phase = phase, noise = .015), rate)
                assertNotNull("$rate ${string.label} $phase", result.frequency)
                assertEquals(0.0, TuningCalculator.cents(result.frequency!!, expected), 2.0)
            }
        }
    }

    @Test fun `light FIR improves measured broadband noise error without a temporal filter`() {
        val lines = mutableListOf("rate,window,note,phase,filtered_error_cents,unfiltered_error_cents")
        var filteredTotal = 0.0
        var rawTotal = 0.0
        for (rate in listOf(44_100, 48_000)) for (size in listOf(4096, 8192)) for (string in StandardTuning.strings)
            for (phase in listOf(.37, .9, 1.2, 2.5)) {
                val samples = PrecisionReferenceTest.signal(string.frequency, rate, size, noise = .1, phase = phase)
                val filtered = YinPitchDetector().detect(samples, rate).frequency!!
                val raw = YinPitchDetector(YinConfig(lowPassFirstZeroHz = null)).detect(samples, rate).frequency!!
                val filteredError = abs(TuningCalculator.cents(filtered, string.frequency))
                val rawError = abs(TuningCalculator.cents(raw, string.frequency))
                lines += "$rate,$size,${string.label},$phase,$filteredError,$rawError"
                filteredTotal += filteredError; rawTotal += rawError
                assertTrue("${string.label} $rate $phase error $filteredError", filteredError < 5.0)
            }
        File("build/reports/precision/filter_comparison.csv").apply {
            parentFile!!.mkdirs(); writeText(lines.joinToString("\n") + "\n")
        }
        assertTrue("filtered=$filteredTotal raw=$rawTotal", filteredTotal < rawTotal)
    }

    @Test fun `transients and slowly drifting nonperiodic signals do not invent pitches`() {
        for (samples in listOf(
            FloatArray(4096) { if (it == 1000) .9f else 0f },
            FloatArray(4096) { it / 4096f - .5f },
            FloatArray(4096) { (.7 * exp(-4.0 * it / 4096)).toFloat() })) {
            assertNull(YinPitchDetector().detect(samples, 44_100).frequency)
        }
    }
}
