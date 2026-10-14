package com.mytune.domain.audio

import org.junit.Assert.*
import org.junit.Test

class PitchSmootherTest {
    @Test fun `median and EMA reduce jitter without changing a stable note`() {
        val smoother = PitchSmoother()
        assertEquals(110.0, smoother.smooth(110.0), .00001)
        val output = (0..20).map { smoother.smooth(if (it % 2 == 0) 110.2 else 109.8) }
        assertTrue(output.max() - output.min() < .4)
    }

    @Test fun `octave jumps reset instead of passing through another note`() {
        val smoother = PitchSmoother()
        smoother.smooth(82.4)
        assertEquals(164.8, smoother.smooth(164.8), .000001)
        smoother.reset()
        assertEquals(110.0, smoother.smooth(110.0), .000001)
    }
}
