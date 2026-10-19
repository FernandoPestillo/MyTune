package com.mytune.domain.audio

import com.mytune.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AutomaticStringSelectorTest {
    private val classifier = GuitarStringClassifier()
    private val selector = AutomaticStringSelector()
    private val e2 = StandardTuning.strings.first()
    private fun candidate(string: GuitarStringNote) = classifier.classify(PitchDetection(string.frequency, .99, .2, SignalQuality.PITCHED))

    @Test fun `all six strings confirm after real elapsed evidence rather than callback count`() {
        for (string in StandardTuning.strings) {
            selector.reset()
            for (time in listOf(0L, 46, 92))
                assertNull(selector.accept(candidate(string), null, time).acceptedString)
            assertEquals(string, selector.accept(candidate(string), null, 138).acceptedString)
        }
    }
    @Test fun `rapid overlapping callbacks cannot confirm a selection`() {
        repeat(120) { assertNull(selector.accept(candidate(e2), null, it.toLong()).acceptedString) }
        assertEquals(e2, selector.accept(candidate(e2), null, 120).acceptedString)
        val sparse = AutomaticStringSelector()
        sparse.accept(candidate(e2), null, 0)
        assertEquals(e2, sparse.accept(candidate(e2), null, 120).acceptedString)
    }
    @Test fun `switching need not wait for retained old target and requires continuous evidence`() {
        val a2 = StandardTuning.strings[1]
        for (time in listOf(0L, 46, 92)) assertNull(selector.accept(candidate(a2), e2, time).acceptedString)
        assertEquals(a2, selector.accept(candidate(a2), e2, 138).acceptedString)
        assertEquals(a2, selector.accept(candidate(a2), a2, 140).acceptedString)
    }
    @Test fun `invalid isolated octave and returning current string clear a pending change`() {
        val a2 = StandardTuning.strings[1]
        selector.accept(candidate(a2), e2, 0)
        assertEquals(AutomaticSelectionStatus.UNCERTAIN,
            selector.accept(StringDetectionResult(StringClassificationStatus.NO_MATCH), e2, 46).status)
        selector.accept(candidate(a2), e2, 92)
        assertEquals(e2, selector.accept(candidate(e2), e2, 138).acceptedString)
        assertNull(selector.accept(candidate(a2), e2, 184).acceptedString)
    }
    @Test fun `plausible octave changes use longer evidence and sustained E4 remains selectable`() {
        val e4 = StandardTuning.strings.last()
        for (time in listOf(0L, 46, 92, 138)) assertNull(selector.accept(candidate(e4), e2, time).acceptedString)
        assertEquals(e4, selector.accept(candidate(e4), e2, 184).acceptedString)
    }
    @Test fun `gaps resets and inconsistent detuning cannot recycle stale confirmation`() {
        selector.accept(candidate(e2), null, 0)
        assertFalse(selector.advance(201))
        assertNull(selector.accept(candidate(e2), null, 202).acceptedString)
        selector.reset()
        assertNull(selector.accept(candidate(e2), null, 400).acceptedString)
        assertNull(selector.accept(candidate(e2).copy(cents = 100.0), null, 446).acceptedString)
        assertNull(selector.accept(candidate(e2), null, 492).acceptedString)
        assertFalse(selector.advance(491)) // Defensive handling of a clock moving backwards.
    }
}
