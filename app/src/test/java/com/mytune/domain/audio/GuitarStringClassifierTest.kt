package com.mytune.domain.audio

import com.mytune.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GuitarStringClassifierTest {
    private val classifier = GuitarStringClassifier()
    private fun pitch(hz: Double, confidence: Double = .99, rms: Double = .2, quality: SignalQuality = SignalQuality.PITCHED) =
        PitchDetection(hz, confidence, rms, quality)

    @Test fun `all six references and seven detunings are classified using cents`() {
        for (string in StandardTuning.strings) for (cents in listOf(-100, -50, -20, 0, 20, 50, 100)) {
            val result = classifier.classify(pitch(string.frequency * 2.0.pow(cents / 1200.0)))
            assertEquals(StringClassificationStatus.MATCH, result.status)
            assertEquals(string, result.candidate)
            assertEquals(cents.toDouble(), result.cents!!, 1e-8)
        }
    }
    @Test fun `identification includes the boundary but never has an unlimited range`() {
        for (string in StandardTuning.strings) for (sign in listOf(-1, 1)) {
            assertEquals(string, classifier.classify(pitch(string.frequency * 2.0.pow(sign * 150.0 / 1200))).candidate)
            val outside = classifier.classify(pitch(string.frequency * 2.0.pow(sign * 151.0 / 1200)))
            assertEquals(StringClassificationStatus.NO_MATCH, outside.status)
            assertNull(outside.candidate)
        }
        for (hz in listOf(20.0, 55.0, 440.0, 659.255, 2000.0))
            assertEquals(StringClassificationStatus.NO_MATCH, classifier.classify(pitch(hz)).status)
    }
    @Test fun `malformed weak and unreliable detections have no candidate`() {
        val invalid = listOf(PitchDetection(), pitch(0.0), pitch(-110.0), pitch(Double.NaN), pitch(Double.POSITIVE_INFINITY),
            pitch(110.0, .89), pitch(110.0, Double.NaN), pitch(110.0, 1.01), pitch(110.0, rms = 0.0),
            pitch(110.0, rms = Double.NaN), pitch(110.0, quality = SignalQuality.UNRELIABLE), pitch(110.0, quality = SignalQuality.SILENCE))
        invalid.forEach { assertEquals(StringClassificationStatus.INVALID, classifier.classify(it).status) }
    }
    @Test fun `octaves are not folded into string targets`() {
        assertNull(classifier.classify(pitch(StandardTuning.strings[0].frequency * 2)).candidate) // E3
        assertNull(classifier.classify(pitch(220.0)).candidate) // A3
        val e4 = StandardTuning.strings.last()
        assertEquals(e4, classifier.classify(pitch(e4.frequency)).candidate)
        assertNull(classifier.classify(pitch(e4.frequency * 2)).candidate)
    }
    @Test fun `injected overlapping references report ambiguity instead of using list order`() {
        val references = StandardTuning.strings.slice(3..4)
        val overlapping = GuitarStringClassifier(references, StringClassifierConfig(maxDeviationCents = 250.0))
        val halfway = sqrt(references[0].frequency * references[1].frequency)
        val result = overlapping.classify(pitch(halfway))
        assertEquals(StringClassificationStatus.AMBIGUOUS, result.status)
        assertNull(result.candidate)
        assertEquals(references[0], GuitarStringClassifier(listOf(references[0])).classify(pitch(references[0].frequency)).candidate)
    }
}
