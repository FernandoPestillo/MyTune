package com.mytune

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import com.mytune.presentation.*
import com.mytune.ui.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TunerEffectsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun hapticCallbackFiresOnceForFreshSuccessAndIgnoresInvalidSignals(){
        val target=StandardTuning.strings.first()
        var enabled by mutableStateOf(true)
        var state by mutableStateOf(TunerUiState(hasPermission=true,microphoneActive=true))
        var count=0
        compose.setContent{TuningHaptics(state,enabled){count++}}
        val tuned=state.copy(readingStatus=ReadingStatus.ACTIVE,
            result=TuningCalculator.calculate(target.frequency,target,.99),
            rawDetection=PitchDetection(target.frequency,.99,.2,SignalQuality.PITCHED))
        compose.runOnIdle{state=tuned};compose.waitForIdle()
        compose.runOnIdle{assertEquals(1,count);state=tuned.copy(audioLevel=.7f)};compose.waitForIdle()
        compose.runOnIdle{assertEquals(1,count);state=tuned.copy(readingStatus=ReadingStatus.HELD,rawDetection=PitchDetection())}
        compose.waitForIdle();compose.runOnIdle{assertEquals(1,count);enabled=false;state=tuned}
        compose.waitForIdle();compose.runOnIdle{assertEquals(1,count)}
    }
    @Test fun systemMotionObserverRespondsToDisabledAnimationsAndRestoresTheSetting(){
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val resolver=instrumentation.targetContext.contentResolver
        val original=android.provider.Settings.Global.getString(resolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE)
        fun shell(command:String){
            val pfd=instrumentation.uiAutomation.executeShellCommand(command)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use{it.readBytes()}
        }
        var allowed=true
        try{
            shell("settings put global animator_duration_scale 1")
            compose.setContent{val value=systemMotionEnabled();SideEffect{allowed=value}}
            compose.waitUntil{allowed}
            shell("settings put global animator_duration_scale 0")
            compose.waitUntil{!allowed}
            shell("settings put global animator_duration_scale 1")
            compose.waitUntil{allowed}
        }finally{
            shell(if(original==null) "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $original")
        }
    }
    @Test fun stringWaveAnimatesThenSettlesAndStopsForDisabledOrReducedMotion(){
        compose.mainClock.autoAdvance=false
        var level by mutableStateOf(0f)
        var enabled by mutableStateOf(true)
        lateinit var wave:State<Float>
        compose.setContent{wave=rememberStringWave(level,enabled,6)}
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle{assertEquals(0f,wave.value,0f);level=1f}
        compose.mainClock.advanceTimeBy(400)
        var a=0f
        compose.runOnIdle{a=wave.value}
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle{assertTrue("Wave did not move",kotlin.math.abs(wave.value-a)>.01f);level=0f}
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle{assertEquals(0f,wave.value,.01f);level=1f;enabled=false}
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle{assertEquals(0f,wave.value,0f)}
        // The same enabled gate combines user preference and the observed system motion scale.
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle{assertEquals(0f,wave.value,0f)}
    }
}
