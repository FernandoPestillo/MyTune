package com.mytune.domain.audio

import com.mytune.domain.model.StandardTuning
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*
import kotlin.random.Random

/** Reproducible corpus and host-only microbenchmark; never a device performance guarantee. */
class PrecisionReferenceTest {
    private val rows = mutableListOf("scenario,rate,window,note,expected_hz,measured_hz,error_cents,confidence,rms,quality")
    private fun measure(scenario: String, frequency: Double, rate: Int, size: Int, note: String,
        amplitudes: DoubleArray = doubleArrayOf(.6), noise: Double = 0.0, phase: Double = .37,
        decay: Double = 0.0): PitchDetection {
        val result = YinPitchDetector().detect(signal(frequency, rate, size, amplitudes, noise, phase, decay), rate)
        val error = result.frequency?.let { TuningCalculator.cents(it, frequency) }
        rows += "$scenario,$rate,$size,$note,$frequency,${result.frequency ?: ""},${error ?: ""},${result.confidence},${result.rms},${result.quality}"
        return result
    }

    @Test fun `reference corpus measures clean detuning harmonics and noise`() {
        val failures = mutableListOf<String>()
        fun check(result: PitchDetection, expected: Double, label: String, tolerance: Double = 2.0) {
            if (result.frequency == null || abs(TuningCalculator.cents(result.frequency, expected)) > tolerance)
                failures += "$label expected=$expected measured=${result.frequency}"
        }
        for (rate in listOf(44_100, 48_000)) for (size in listOf(4096, 8192)) {
            for (string in StandardTuning.strings) {
                for (cents in listOf(-50, -25, -10, -5, 0, 5, 10, 25, 50)) {
                    val expected = string.frequency * 2.0.pow(cents / 1200.0)
                    check(measure("clean_$cents", expected, rate, size, string.label), expected, "clean ${string.label} $cents")
                }
                for ((profile, amplitudes) in listOf(doubleArrayOf(.22, .55, .18),
                    doubleArrayOf(.08, .75, .025), doubleArrayOf(.12, .08, .65)).withIndex()) {
                    check(measure("harmonic_$profile", string.frequency, rate, size, string.label, amplitudes),
                        string.frequency, "harmonic $profile ${string.label}")
                }
                check(measure("moderate_noise", string.frequency, rate, size, string.label, noise = .1),
                    string.frequency, "noise ${string.label}", 5.0)
                check(measure("soft", string.frequency, rate, size, string.label, doubleArrayOf(.006)),
                    string.frequency, "soft ${string.label}")
                check(measure("decay", string.frequency, rate, size, string.label, doubleArrayOf(.4, .25, .15),
                    phase = 1.2, decay = 3.0), string.frequency, "decay ${string.label}", 5.0)
                val intense = measure("intense_noise", string.frequency, rate, size, string.label, noise = 1.0)
                if (intense.frequency != null) failures += "intense noise should be rejected ${string.label}"
            }
            for (index in 0 until 24) {
                val result = measure("noise_only_$index", 110.0, rate, size, "none", doubleArrayOf(0.0),
                    noise = .2, phase = index.toDouble())
                if (result.frequency != null) failures += "noise false positive $rate $size $index"
            }
        }
        report("corpus.csv", rows.joinToString("\n") + "\n")
        report("failures.txt", failures.joinToString("\n"))
        assertTrue("${failures.size} failures: ${failures.take(10)}", failures.isEmpty())
    }

    @Suppress("DEPRECATION")
    @Test fun `host benchmark compares window sizes without a timing assertion`() {
        val output = mutableListOf("rate,window,frequency,median_ms,p95_ms,allocated_bytes_per_detection")
        // Android's compile stubs omit java.lang.management; reflection is host-test only.
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val allocationApi = Class.forName("com.sun.management.ThreadMXBean")
        allocationApi.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(bean, true)
        fun allocated(thread: Long) = allocationApi.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
            .invoke(bean, thread) as Long
        for (rate in listOf(44_100, 48_000)) for (size in listOf(4096, 8192)) for (hz in listOf(82.406889, 329.627557)) {
            val detector = YinPitchDetector()
            val samples = signal(hz, rate, size)
            repeat(100) { detector.detect(samples, rate) }
            val times = DoubleArray(100)
            val thread = Thread.currentThread().id
            val bytesBefore = allocated(thread)
            repeat(times.size) {
                val start = System.nanoTime()
                detector.detect(samples, rate)
                times[it] = (System.nanoTime() - start) / 1e6
            }
            val bytesAfter = allocated(thread)
            times.sort()
            output += "$rate,$size,$hz,${times[50]},${times[95]},${if (bytesBefore >= 0) (bytesAfter - bytesBefore) / 100.0 else -1}"
        }
        report("benchmark.csv", output.joinToString("\n") + "\n")
        report("environment.txt", "JVM=${System.getProperty("java.version")} OS=${System.getProperty("os.name")} arch=${System.getProperty("os.arch")}\n")
    }

    private fun report(name: String, text: String) {
        File("build/reports/precision/$name").apply { parentFile!!.mkdirs(); writeText(text) }
    }

    companion object {
        fun signal(frequency: Double, rate: Int = 44_100, size: Int = 4096,
            amplitudes: DoubleArray = doubleArrayOf(.6), noise: Double = 0.0,
            phase: Double = .37, decay: Double = 0.0): FloatArray {
            val random = Random(81 + (phase * 1000).toInt())
            return FloatArray(size) { i ->
                val angle = 2 * PI * frequency * i / rate + phase
                var value = 0.0
                for (h in amplitudes.indices) value += amplitudes[h] * sin((h + 1) * angle)
                (value * exp(-decay * i / size) + noise * random.nextDouble(-1.0, 1.0)).toFloat()
            }
        }
    }
}
