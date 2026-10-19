package com.mytune.diagnostics

import com.mytune.domain.audio.*
import org.junit.Assert.*
import org.junit.Test

class AudioPerformanceMetricsTest {
    @Test fun `disabled diagnostics never read a clock or emit reports`() {
        val metrics = AudioPerformanceMetrics(clockNs = { error("disabled clock") }, report = { error("disabled log") })
        metrics.reset(); metrics.uiUpdate(); metrics.droppedWindow()
        metrics.detection(PitchDetection(), 0, 1, 1); metrics.window(0, 0, 1)
        assertEquals(0, metrics.nowNs())
        assertEquals(0, metrics.snapshot().windows)
    }
    @Test fun `monotonic durations rejection octave candidates queue and UI are independent`() {
        var time = 0L
        val logs = mutableListOf<String>()
        val metrics = AudioPerformanceMetrics(true, { time }, logs::add)
        metrics.reset()
        fun detection(hz: Double?, start: Long, end: Long) = metrics.detection(
            PitchDetection(hz, .99, .2, if (hz == null) SignalQuality.UNRELIABLE else SignalQuality.PITCHED), start, end, 10_000_000)
        detection(110.0, 1_000_000, 5_000_000)
        detection(220.0, 20_000_000, 36_000_000)
        detection(null, 40_000_000, 43_000_000)
        metrics.window(0, 1_000_000, 5_000_000)
        metrics.window(17_000_000, 20_000_000, 36_000_000)
        repeat(3) { metrics.uiUpdate() }; metrics.droppedWindow()
        time = 2_000_000_000
        val result = metrics.snapshot()
        assertEquals(3, result.windows); assertEquals(2, result.valid); assertEquals(1, result.rejected)
        assertEquals(1, result.possibleOctaveJumps); assertEquals(1, result.overruns); assertEquals(1, result.droppedWindows)
        assertEquals(23.0 / 3, result.meanProcessingMs, 1e-9)
        assertEquals(2.0, result.meanQueueMs, 0.0)
        assertEquals(19.0, result.maxWindowToResultMs, 0.0)
        assertEquals(31.0, result.lastValidIntervalMs!!, 0.0)
        assertEquals(1.5, result.uiHz, 0.0)
        metrics.window(time, time, time)
        assertEquals(1, logs.size)
        metrics.window(time, time, time)
        assertEquals(1, logs.size)
        metrics.reset(); assertEquals(0, metrics.snapshot().windows)
    }
}
