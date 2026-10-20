package com.mytune.presentation

import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class AutomaticAudioPipelineTest {
    @Test fun `YIN classifier selector and presentation recognize all strings and seven detunings`() {
        val rows = mutableListOf("rate,note,detuning_cents,selected_note,measured_hz,error_cents,confirmation_ms")
        for (rate in listOf(44_100, 48_000)) for (string in StandardTuning.strings)
            for (detuning in listOf(-100, -50, -20, 0, 20, 50, 100)) {
                val detector = YinPitchDetector()
                val classifier = GuitarStringClassifier()
                val selector = AutomaticStringSelector()
                val presentation = DetectionStabilizer()
                var selected: GuitarStringNote? = null
                var result: DetectionPresentation? = null
                var confirmedMs: Long? = null
                val hz = string.frequency * 2.0.pow(detuning / 1200.0)
                val hopMs = 2048.0 * 1000 / rate
                repeat(5) { frame ->
                    val nowMs = (frame * hopMs).roundToLong()
                    val phase = 2 * PI * hz * frame * 2048 / rate
                    val detection = detector.detect(PrecisionReferenceTest.signal(hz, rate,
                        amplitudes = doubleArrayOf(.08, .75, .025), phase = phase, noise = .005), rate)
                    val decision = selector.accept(classifier.classify(detection), selected, nowMs)
                    decision.acceptedString?.let { target ->
                        if (target != selected) { presentation.reset(); confirmedMs = nowMs }
                        selected = target
                        result = presentation.accept(detection, target, nowMs)
                    }
                }
                assertEquals(string, selected)
                assertNotNull(result?.result)
                val measured = result!!.result!!.frequency
                val error = TuningCalculator.cents(measured, hz)
                assertEquals(detuning.toDouble(), result.result.cents, 2.0)
                assertTrue(abs(error) <= 2.0)
                assertTrue(confirmedMs!! <= 250)
                rows += "$rate,${string.label},$detuning,${selected!!.label},$measured,$error,$confirmedMs"
            }
        File("build/reports/automatic/v1_2_pipeline.csv").apply {
            parentFile!!.mkdirs(); writeText(rows.joinToString("\n") + "\n")
        }
    }
}
