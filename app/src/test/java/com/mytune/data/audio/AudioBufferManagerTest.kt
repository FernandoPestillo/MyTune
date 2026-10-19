package com.mytune.data.audio

import com.mytune.domain.audio.AudioProcessingConfig
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AudioBufferManagerTest {
    @Test fun `partial reads preserve chronological overlapping windows through many wraps`() {
        for (config in listOf(AudioProcessingConfig(), AudioProcessingConfig(8192, 2048), AudioProcessingConfig(32, 7))) {
            val ring = AudioBufferManager(config)
            val random = Random(28)
            val snapshot = FloatArray(config.windowSize)
            var total = 0
            var windows = 0
            while (total < 25_000) {
                val count = minOf(random.nextInt(1, 2049), ring.samplesUntilWindow, 25_000 - total)
                val input = ShortArray(count) { (total + it - 12_000).toShort() }
                val ready = ring.append(input, count)
                total += count
                if (ready) {
                    ring.copyWindow(snapshot)
                    for (i in snapshot.indices) assertEquals((total - config.windowSize + i - 12_000) / 32768f, snapshot[i], 0f)
                    windows++
                    assertEquals(config.windowSize + (windows - 1) * config.hopSize, total)
                }
            }
            assertTrue(windows > 0)
        }
    }
}
