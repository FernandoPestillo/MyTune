package com.mytune.data.audio

import com.mytune.domain.audio.AudioProcessingConfig

/** Single writer PCM ring; snapshots belong to the consumer, never to the ring. */
class AudioBufferManager(private val config: AudioProcessingConfig = AudioProcessingConfig()) {
    private val ring = FloatArray(config.windowSize)
    private var position = 0
    private var filled = 0
    private var sinceWindow = 0
    val samplesUntilWindow: Int get() = if (filled < ring.size) ring.size - filled else config.hopSize - sinceWindow

    /** Caller limits the read so one append cannot cross multiple analysis boundaries. */
    fun append(samples: ShortArray, count: Int): Boolean {
        require(count in 1..minOf(samples.size, samplesUntilWindow))
        for (i in 0 until count) {
            ring[position] = samples[i] / 32768f
            position = (position + 1) % ring.size
        }
        val wasFull = filled == ring.size
        filled = (filled + count).coerceAtMost(ring.size)
        if (wasFull) sinceWindow += count
        if (filled == ring.size && (!wasFull || sinceWindow == config.hopSize)) {
            sinceWindow = 0
            return true
        }
        return false
    }

    fun copyWindow(destination: FloatArray) {
        require(filled == ring.size && destination.size == ring.size)
        val tail = ring.size - position
        ring.copyInto(destination, 0, position, ring.size)
        ring.copyInto(destination, tail, 0, position)
    }
}
