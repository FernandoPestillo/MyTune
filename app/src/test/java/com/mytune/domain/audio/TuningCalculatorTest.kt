package com.mytune.domain.audio

import com.mytune.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class TuningCalculatorTest {
    @Test fun `standard tuning uses precise equal temperament frequencies`() {
        val expected = listOf(82.406889, 110.0, 146.832384, 195.997718, 246.941651, 329.627557)
        StandardTuning.strings.zip(expected).forEach { (string, frequency) -> assertEquals(frequency, string.frequency, .000001) }
    }

    @Test fun `nearest note includes actual octave and accidentals`() {
        val cases = mapOf(82.41 to "E2", 110.0 to "A2", 146.83 to "D3", 196.0 to "G3",
            246.94 to "B3", 329.63 to "E4", 440.0 to "A4", 466.163762 to "A♯4", 65.40639 to "C2")
        cases.forEach { (frequency, label) -> assertEquals(label, TuningCalculator.note(frequency).label) }
    }

    @Test fun `cents are signed and logarithmic`() {
        assertEquals(0.0, TuningCalculator.cents(110.0, 110.0), .000001)
        assertEquals(1200.0, TuningCalculator.cents(220.0, 110.0), .000001)
        assertEquals(-1200.0, TuningCalculator.cents(55.0, 110.0), .000001)
        for (cents in listOf(-31.0, -5.0, 5.0, 27.0)) {
            assertEquals(cents, TuningCalculator.cents(110 * 2.0.pow(cents / 1200), 110.0), .000001)
        }
    }

    @Test fun `tolerance distinguishes sharp flat and tuned`() {
        val target = StandardTuning.strings[0]
        fun status(cents: Double) = TuningCalculator.calculate(target.frequency * 2.0.pow(cents / 1200), target, .99).status
        assertEquals(TuningStatus.IN_TUNE, status(0.0))
        assertEquals(TuningStatus.IN_TUNE, status(4.9))
        assertEquals(TuningStatus.IN_TUNE, status(-4.9))
        assertEquals(TuningStatus.IN_TUNE, status(5.0))
        assertEquals(TuningStatus.IN_TUNE, status(-5.0))
        assertEquals(TuningStatus.SHARP, status(5.1))
        assertEquals(TuningStatus.FLAT, status(-5.1))
    }

    @Test fun `another note or octave can never be in tune`() {
        val target = StandardTuning.strings[0]
        for (frequency in listOf(target.frequency * 2, target.frequency / 2, 110.0)) {
            assertEquals(TuningStatus.WRONG_NOTE, TuningCalculator.calculate(frequency, target, .99).status)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid frequency is rejected`() { TuningCalculator.note(Double.NaN) }

    @Test(expected = IllegalArgumentException::class)
    fun `zero target is rejected`() { TuningCalculator.cents(110.0, 0.0) }
}
