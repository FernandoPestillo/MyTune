package com.mytune.domain.audio

import com.mytune.domain.tuning.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class AlternatePitchDetectionTest {
    @Test fun `low notes and entire editor range are actually detected with harmonic and noisy signals`() {
        val rows = mutableListOf("rate,window,midi,detuning_cents,profile,allowed,measured_hz,error_cents,confidence")
        val failures = mutableListOf<String>()
        // Continue the exploratory scan above the allowed range and retain evidence of its limits.
        for (rate in listOf(44_100,48_000)) for (size in listOf(4096,8192))
            for (midi in TuningValidator.MIN_MIDI..83) for (detuning in listOf(-100,0,100)) {
                val hz = PitchFrequencyCalculator.frequency(midi) * 2.0.pow(detuning / 1200.0)
                for ((profile, harmonics) in listOf(doubleArrayOf(.6), doubleArrayOf(.08,.75,.025)).withIndex()) {
                    val result = YinPitchDetector().detect(PrecisionReferenceTest.signal(hz, rate, size,
                        amplitudes = harmonics, noise = if (profile == 0) .02 else .005), rate)
                    val cents = result.frequency?.let { TuningCalculator.cents(it, hz) }
                    val allowed = midi <= TuningValidator.MAX_MIDI
                    if (allowed && (cents == null || abs(cents) > 2)) failures += "$rate $size midi=$midi detune=$detuning profile=$profile error=$cents"
                    rows += "$rate,$size,$midi,$detuning,$profile,$allowed,${result.frequency ?: ""},${cents ?: ""},${result.confidence}"
                }
            }
        File("build/reports/tunings/pitch.csv").apply { parentFile!!.mkdirs(); writeText(rows.joinToString("\n")+"\n") }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
    @Test fun `each preset classifies each open string and exact unisons remain ambiguous regardless of order`() {
        for (tuning in TuningPresets.all) for (string in tuning.strings) {
            val result = GuitarStringClassifier(tuning.strings).classify(PitchDetection(string.frequency,.99,.2,SignalQuality.PITCHED))
            assertEquals(string, result.candidate)
        }
        val unison = TuningValidator.custom("unison", "Uníssono", listOf(40,40,50,55,59,64))
        for (list in listOf(unison.strings, unison.strings.reversed())) {
            val result = GuitarStringClassifier(list).classify(PitchDetection(82.406889,.99,.2,SignalQuality.PITCHED))
            assertEquals(com.mytune.domain.model.StringClassificationStatus.AMBIGUOUS, result.status)
            assertNull(result.candidate)
        }
        val close = TuningValidator.custom("close", "Próximas", listOf(40,41,50,55,59,64))
        assertEquals(com.mytune.domain.model.StringClassificationStatus.AMBIGUOUS,
            GuitarStringClassifier(close.strings).classify(PitchDetection(82.406889,.99,.2,SignalQuality.PITCHED)).status)
        assertNull(GuitarStringClassifier(TuningPresets.find("drop-c")!!.strings)
            .classify(PitchDetection(82.406889,.99,.2,SignalQuality.PITCHED)).candidate)
    }
    @Test fun `C2 D2 Eb2 E2 retain precision in clean harmonic and moderate noise cases`() {
        val rows = mutableListOf("rate,window,midi,scenario,phase,measured_hz,error_cents,confidence")
        for (rate in listOf(44_100,48_000)) for (size in listOf(4096,8192)) for (midi in listOf(36,38,39,40))
            for (scenario in 0..2) for (phase in listOf(.1,.8,1.5)) {
                val hz = PitchFrequencyCalculator.frequency(midi)
                val amplitudes = if (scenario == 1) doubleArrayOf(.08,.75,.025) else doubleArrayOf(.6)
                val result = YinPitchDetector().detect(PrecisionReferenceTest.signal(hz,rate,size,amplitudes,
                    noise = if (scenario == 2) .1 else 0.0,phase = phase),rate)
                assertNotNull("$rate $size $midi $scenario",result.frequency)
                val error = TuningCalculator.cents(result.frequency!!,hz)
                assertTrue("$midi scenario=$scenario cents=$error",abs(error) <= if (scenario==2) 5 else 2)
                rows += "$rate,$size,$midi,$scenario,$phase,${result.frequency},$error,${result.confidence}"
            }
        File("build/reports/tunings/low.csv").apply { parentFile!!.mkdirs(); writeText(rows.joinToString("\n")+"\n") }
    }
    @Test fun `host window comparison records processing cost without claiming Android latency`() {
        val lines = mutableListOf("rate,window,note,median_processing_ms,p95_processing_ms,window_ms,hop_ms")
        for (rate in listOf(44_100,48_000)) for (size in listOf(4096,8192)) {
            val detector = YinPitchDetector()
            val samples = PrecisionReferenceTest.signal(PitchFrequencyCalculator.frequency(36),rate,size)
            repeat(100) { detector.detect(samples,rate) }
            val times = DoubleArray(50) {
                val start = System.nanoTime(); detector.detect(samples,rate); (System.nanoTime()-start)/1e6
            }.sorted()
            lines += "$rate,$size,C2,${times[25]},${times[47]},${size*1000.0/rate},${2048*1000.0/rate}"
        }
        File("build/reports/tunings/windows.csv").apply { parentFile!!.mkdirs(); writeText(lines.joinToString("\n")+"\n") }
    }

}
