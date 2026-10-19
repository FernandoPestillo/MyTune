package com.mytune.domain.tuning

import kotlin.math.*

/** Single fixed reference for v1.3. No user calibration or chromatic mode is exposed. */
object PitchFrequencyCalculator {
    const val A4_HZ = 440.0
    private val sharp = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val flat = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
    val noteNames: List<String> get() = sharp
    fun frequency(midi: Int): Double = A4_HZ * 2.0.pow((midi - 69) / 12.0)
    fun midi(frequency: Double): Int {
        require(frequency.isFinite() && frequency > 0)
        return (69 + 12 * log2(frequency / A4_HZ)).roundToInt()
    }
    fun name(midi: Int, preferFlats: Boolean = false): String = (if (preferFlats) flat else sharp)[Math.floorMod(midi, 12)]
    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1
}
