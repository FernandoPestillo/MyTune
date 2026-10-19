package com.mytune.presentation

import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioPipelineTest {
    @Test fun `real YIN and presentation track sustain decay gaps and sustained note changes`() {
        for (rate in listOf(44_100, 48_000)) {
            val detector = YinPitchDetector()
            val presentation = DetectionStabilizer()
            val target = StandardTuning.strings.first()
            var time = 0L
            fun accept(hz: Double, amplitude: Double = .4, noise: Double = .01): DetectionPresentation {
                val phase = 2 * PI * hz * time / 1000
                val detection = detector.detect(PrecisionReferenceTest.signal(hz, rate,
                    amplitudes = doubleArrayOf(amplitude, amplitude * .6), noise = noise, phase = phase), rate)
                return presentation.accept(detection, target, time).also { time += 46 }
            }
            val sustained = (0..30).map { accept(target.frequency * 2.0.pow((if (it % 2 == 0) .8 else -.8) / 1200)).result!!.cents }
            assertTrue(sustained.max() - sustained.min() < 2)
            // A real tuning adjustment must settle promptly, not wait for the hold timeout.
            repeat(4) { accept(target.frequency * 2.0.pow(25.0 / 1200)) }
            val changed = accept(target.frequency * 2.0.pow(25.0 / 1200)).result!!
            assertEquals(25.0, changed.cents, 2.0)
            assertEquals(TuningStatus.SHARP, changed.status)
            for (amplitude in listOf(.2, .1, .03, .006)) assertNotNull(accept(target.frequency, amplitude, noise = 0.0).result)
            val beforeGap = presentation.advance(time).result
            time += 300
            assertEquals(ReadingStatus.HELD, presentation.advance(time).readingStatus)
            assertEquals(beforeGap, presentation.advance(time).result)
            assertEquals(ReadingStatus.ACTIVE, accept(target.frequency).readingStatus)
            // Two real octave readings are accepted, with no average between E2 and E3.
            val initial = presentation.advance(time).result
            assertEquals(initial, accept(target.frequency * 2).result)
            val octave = accept(target.frequency * 2).result!!
            assertEquals("E3", octave.note.label)
            assertEquals(TuningStatus.WRONG_NOTE, octave.status)
            assertEquals(target.frequency * 2, octave.frequency, .1)
            time += 1200
            assertEquals(ReadingStatus.STALE, presentation.advance(time).readingStatus)
            time += 600
            assertNull(presentation.advance(time).result)
        }
    }

    @Test fun `stale tuned result requires reentry threshold on a new detection`() {
        val stabilizer = DetectionStabilizer()
        val target = StandardTuning.strings.first()
        fun pitch(cents: Double, ms: Long) = stabilizer.accept(PitchDetection(target.frequency * 2.0.pow(cents / 1200),
            .99, .2, SignalQuality.PITCHED), target, ms)
        assertEquals(TuningStatus.IN_TUNE, pitch(0.0, 0).result!!.status)
        assertEquals(ReadingStatus.STALE, stabilizer.advance(1200).readingStatus)
        assertEquals(TuningStatus.SHARP, pitch(6.0, 1300).result!!.status)
    }
}
