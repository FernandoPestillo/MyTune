package com.mytune.presentation

import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class DetectionStabilizerTest {
    private val target = StandardTuning.strings.first()
    private val stabilizer = DetectionStabilizer()
    private fun pitch(hz: Double, time: Long = 0) = stabilizer.accept(
        PitchDetection(hz, .99, .2, SignalQuality.PITCHED), target, time)
    private fun cents(value: Double, time: Long = 0) = pitch(target.frequency * 2.0.pow(value / 1200), time)

    @Test fun `flat sharp and exact target retain correct direction`() {
        assertEquals(TuningStatus.FLAT, pitch(80.50).result?.status)
        stabilizer.reset()
        assertEquals(TuningStatus.SHARP, pitch(84.0).result?.status)
        stabilizer.reset()
        assertEquals(TuningStatus.IN_TUNE, pitch(target.frequency).result?.status)
    }

    @Test fun `hysteresis enters at five and leaves beyond seven cents`() {
        assertEquals(TuningStatus.IN_TUNE, cents(5.0).result?.status)
        listOf(5.5, 4.5, 6.9, 5.1, 7.0).forEach {
            assertEquals(TuningStatus.IN_TUNE, cents(it).result?.status)
        }
        assertEquals(TuningStatus.SHARP, cents(7.1).result?.status)
        assertEquals(TuningStatus.SHARP, cents(6.0).result?.status)
        repeat(12) { cents(4.0) }
        assertEquals(TuningStatus.IN_TUNE, cents(4.0).result?.status)
    }

    @Test fun `isolated octave error is held and confirmed octave is never tuned or averaged`() {
        val initial = pitch(target.frequency).result
        assertEquals(initial, pitch(target.frequency * 2, 50).result)
        assertEquals(initial, pitch(target.frequency, 100).result)
        pitch(target.frequency * 2, 150)
        val changed = pitch(target.frequency * 2, 200).result!!
        assertEquals("E3", changed.note.label)
        assertEquals(TuningStatus.WRONG_NOTE, changed.status)
        assertEquals(target.frequency * 2, changed.frequency, 1e-9)
    }

    @Test fun `confirmed adjacent notes reset even below old eighty cent threshold`() {
        cents(49.0)
        cents(51.0, 50)
        val changed = cents(51.0, 100).result!!
        assertEquals(41, changed.note.midi)
        assertEquals(51.0, changed.cents, 1e-8)
        assertEquals(TuningStatus.WRONG_NOTE, changed.status)
    }

    @Test fun `low confidence and malformed readings do not renew last valid time`() {
        val initial = pitch(80.0).result
        listOf(PitchDetection(82.41, .5, .2, SignalQuality.PITCHED),
            PitchDetection(Double.NaN, .99, .2, SignalQuality.PITCHED),
            PitchDetection(82.41, .99, .2, SignalQuality.UNRELIABLE)).forEach {
            assertEquals(initial, stabilizer.accept(it, target, 1000).result)
        }
        assertNull(stabilizer.advance(1800).result)
    }

    @Test fun `jitter is smoothed and constant detuning converges accurately`() {
        cents(0.0)
        val output = (0..30).map { cents(if (it % 2 == 0) 2.0 else -2.0).result!!.cents }
        assertTrue(output.max() - output.min() < 4.0)
        repeat(25) { cents(-25.0) }
        val result = cents(-25.0).result!!
        assertEquals(-25.0, result.cents, .001)
        assertEquals(TuningStatus.FLAT, result.status)
    }

    @Test fun `expired reading and explicit reset both discard old smoothing and hysteresis`() {
        cents(0.0)
        assertNull(stabilizer.advance(1800).result)
        assertEquals(25.0, cents(25.0, 1900).result!!.cents, 1e-8)
        stabilizer.reset()
        assertEquals(-25.0, cents(-25.0, 1950).result!!.cents, 1e-8)
    }

    @Test fun `timings and tolerance can be configured`() {
        val custom = DetectionStabilizer(DetectionStabilityConfig(detectionHoldDurationMs = 200,
            noSignalTimeoutMs = 400, enterInTuneCents = 3.0, exitInTuneCents = 4.0))
        custom.accept(PitchDetection(target.frequency, .99, .2, SignalQuality.PITCHED), target, 0)
        assertEquals(ReadingStatus.HELD, custom.advance(199).readingStatus)
        assertEquals(ReadingStatus.STALE, custom.advance(200).readingStatus)
        assertEquals(ReadingStatus.ABSENT, custom.advance(400).readingStatus)
    }
}
