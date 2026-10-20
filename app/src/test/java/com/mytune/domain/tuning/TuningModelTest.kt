package com.mytune.domain.tuning

import com.mytune.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TuningModelTest {
    @Test fun `all nine presets have exact physical identities and MIDI targets`() {
        val expected = mapOf(
            "e-standard" to listOf(40,45,50,55,59,64), "drop-d" to listOf(38,45,50,55,59,64),
            "eb-standard" to listOf(39,44,49,54,58,63), "d-standard" to listOf(38,43,48,53,57,62),
            "drop-c" to listOf(36,43,48,53,57,62), "open-d" to listOf(38,45,50,54,57,62),
            "open-g" to listOf(38,43,50,55,59,62), "open-e" to listOf(40,47,52,56,59,64),
            "open-a" to listOf(40,45,52,57,61,64))
        assertEquals(expected.keys, TuningPresets.all.map { it.id }.toSet())
        for (tuning in TuningPresets.all) {
            assertEquals((6 downTo 1).toList(), tuning.strings.map { it.number })
            assertEquals(expected[tuning.id], tuning.strings.map { it.midi })
            assertNull(TuningValidator.error(tuning)); assertFalse(tuning.isCustom)
        }
        assertEquals("E♭2", TuningPresets.find("eb-standard")!!.string(6).label)
        assertEquals("F♯3", TuningPresets.find("open-d")!!.string(3).label)
        assertSame(TuningPresets.standard.strings, StandardTuning.strings)
    }
    @Test fun `frequency octave and enharmonic displays share one fixed reference`() {
        for ((midi, expected) in listOf(36 to 65.406391325,38 to 73.416191979,39 to 77.781745931,
            40 to 82.406889228,45 to 110.0,64 to 329.627556913)) {
            assertEquals(expected, PitchFrequencyCalculator.frequency(midi), 1e-8)
            assertEquals(midi, PitchFrequencyCalculator.midi(expected))
        }
        assertEquals("D♯", PitchFrequencyCalculator.name(39))
        assertEquals("E♭", PitchFrequencyCalculator.name(39, true))
        assertEquals(2, PitchFrequencyCalculator.octave(39))
        assertEquals(440.0, PitchFrequencyCalculator.frequency(69), 0.0)
    }
    @Test fun `validation rejects incomplete corrupt or unsupported targets but permits unison and unusual order`() {
        val valid = TuningValidator.custom("custom-test", "Pessoal", listOf(64,40,40,50,55,59))
        assertNull(TuningValidator.error(valid))
        for (bad in listOf(valid.copy(name = " "), valid.copy(name = "a".repeat(61)),
            valid.copy(strings = valid.strings.drop(1)), valid.copy(strings = valid.strings + valid.strings.first()),
            valid.copy(strings = valid.strings.map { it.copy(number = 6) }),
            TuningValidator.custom("x", "Fora", listOf(34,45,50,55,59,64)),
            TuningValidator.custom("x", "Fora", listOf(40,45,50,55,59,77)),
            valid.copy(id = "e-standard"), TuningPresets.standard.copy(name = "Alterado"))) {
            assertNotNull("$bad", TuningValidator.error(bad))
        }
    }
}
