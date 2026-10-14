package com.mytune

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mytune.data.audio.AudioCaptureManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Exercises actual AudioRecord allocation, PCM delivery, cancellation and reopening.
 * This does not validate acoustic pitch accuracy or stand in for a physical guitar test.
 */
@RunWith(AndroidJUnit4::class)
class AudioCaptureManagerTest {
    @get:Rule val activity = createAndroidComposeRule<ComponentActivity>()
    @Test fun actualRecorderDeliversFramesAndCanBeReopened() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val manager = AudioCaptureManager(context)
        repeat(2) {
            val received = CompletableDeferred<Int>()
            val capture = launch {
                manager.capture(onStarted = { assertTrue(it == 44_100 || it == 48_000) },
                    onSamples = { samples, _ ->
                        assertEquals(4096, samples.size)
                        assertTrue(samples.all { it.isFinite() && it in -1f..1f })
                        received.complete(samples.size)
                    })
            }
            try { assertEquals(4096, withTimeout(8_000) { received.await() }) }
            finally { capture.cancelAndJoin() }
        }
    }
}
