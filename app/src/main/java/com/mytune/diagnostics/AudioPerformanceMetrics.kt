package com.mytune.diagnostics

import com.mytune.domain.audio.PitchDetection
import com.mytune.domain.audio.TuningCalculator
import kotlin.math.abs

/** Opt-in aggregate diagnostics. No samples, recordings, device IDs or user data are retained.
 * All durations use the same monotonic clock. Absolute pitch error needs external ground truth.
 */
class AudioPerformanceMetrics(
    val enabled: Boolean = false,
    private val clockNs: () -> Long = System::nanoTime,
    private val report: (String) -> Unit = {},
) {
    data class Snapshot(
        val windows: Long = 0, val valid: Long = 0, val rejected: Long = 0,
        val possibleOctaveJumps: Long = 0, val droppedWindows: Long = 0, val overruns: Long = 0,
        val meanProcessingMs: Double = 0.0, val maxProcessingMs: Double = 0.0,
        val meanQueueMs: Double = 0.0, val maxWindowToResultMs: Double = 0.0,
        val lastValidIntervalMs: Double? = null, val uiUpdates: Long = 0, val uiHz: Double = 0.0,
        val frequencyHz: Double? = null, val note: String? = null, val nearestNoteCents: Double? = null,
        val confidence: Double = 0.0, val rms: Double = 0.0,
    )
    private var state = Snapshot()
    private var startedNs = 0L
    private var lastLogNs = 0L
    private var lastValidNs: Long? = null
    private var previousHz: Double? = null
    private var timedWindows = 0L
    private var totalQueueMs = 0.0

    @Synchronized fun reset() {
        if (!enabled) return
        state = Snapshot(); startedNs = clockNs(); lastLogNs = startedNs
        lastValidNs = null; previousHz = null; timedWindows = 0; totalQueueMs = 0.0
    }

    fun nowNs(): Long = if (enabled) clockNs() else 0L

    @Synchronized fun detection(result: PitchDetection, started: Long, finished: Long, hopNs: Long) {
        if (!enabled) return
        val duration = (finished - started).coerceAtLeast(0) / 1e6
        val hz = result.frequency
        val valid = hz != null
        val prior = previousHz
        val jump = if (hz != null && prior != null) abs(TuningCalculator.cents(hz, prior)) else 0.0
        val possibleOctave = abs(jump - 1200) < 60 || abs(jump - 2400) < 60
        val note = hz?.let(TuningCalculator::note)
        val nearest = hz?.let { 440.0 * Math.pow(2.0, (note!!.midi - 69) / 12.0) }
        val count = state.windows + 1
        state = state.copy(windows = count, valid = state.valid + if (valid) 1 else 0,
            rejected = state.rejected + if (valid) 0 else 1,
            possibleOctaveJumps = state.possibleOctaveJumps + if (possibleOctave) 1 else 0,
            overruns = state.overruns + if (finished - started > hopNs) 1 else 0,
            meanProcessingMs = state.meanProcessingMs + (duration - state.meanProcessingMs) / count,
            maxProcessingMs = maxOf(state.maxProcessingMs, duration),
            lastValidIntervalMs = if (valid) lastValidNs?.let { (finished - it) / 1e6 } else state.lastValidIntervalMs,
            frequencyHz = hz, note = note?.label, nearestNoteCents = hz?.let { TuningCalculator.cents(it, nearest!!) },
            confidence = result.confidence, rms = result.rms)
        if (valid) { lastValidNs = finished; previousHz = hz }
    }

    @Synchronized fun window(available: Long, started: Long, finished: Long) {
        if (!enabled) return
        timedWindows++
        totalQueueMs += (started - available).coerceAtLeast(0) / 1e6
        state = state.copy(meanQueueMs = totalQueueMs / timedWindows,
            maxWindowToResultMs = maxOf(state.maxWindowToResultMs, (finished - available).coerceAtLeast(0) / 1e6))
        if (finished - lastLogNs >= 2_000_000_000L) {
            lastLogNs = finished
            val current = snapshot()
            report("{\"event\":\"audio_metrics\",\"windows\":${current.windows},\"valid\":${current.valid}," +
                "\"rejected\":${current.rejected},\"possibleOctaveJumps\":${current.possibleOctaveJumps}," +
                "\"droppedWindows\":${current.droppedWindows},\"overruns\":${current.overruns}," +
                "\"meanProcessingMs\":${current.meanProcessingMs},\"maxProcessingMs\":${current.maxProcessingMs}," +
                "\"meanQueueMs\":${current.meanQueueMs},\"maxWindowToResultMs\":${current.maxWindowToResultMs}," +
                "\"lastValidIntervalMs\":${current.lastValidIntervalMs},\"uiUpdates\":${current.uiUpdates},\"uiHz\":${current.uiHz}," +
                "\"frequencyHz\":${current.frequencyHz},\"note\":${current.note?.let { "\"$it\"" } ?: "null"}," +
                "\"nearestNoteCents\":${current.nearestNoteCents},\"confidence\":${current.confidence},\"rms\":${current.rms}}")
        }
    }

    @Synchronized fun droppedWindow() {
        if (enabled) state = state.copy(droppedWindows = state.droppedWindows + 1)
    }
    @Synchronized fun uiUpdate() {
        if (enabled) state = state.copy(uiUpdates = state.uiUpdates + 1)
    }
    @Synchronized fun snapshot(): Snapshot {
        if (!enabled) return state
        val elapsed = (clockNs() - startedNs) / 1e9
        return state.copy(uiHz = if (elapsed > 0) state.uiUpdates / elapsed else 0.0)
    }
    @Synchronized fun captureConfiguration(source: Int, rate: Int, nativeBufferFrames: Int, window: Int, hop: Int,
        effects: String) {
        if (enabled) report("{\"event\":\"audio_capture\",\"source\":$source,\"rate\":$rate,\"nativeBufferFrames\":$nativeBufferFrames," +
            "\"window\":$window,\"hop\":$hop,\"effects\":\"$effects\"}")
    }
}
