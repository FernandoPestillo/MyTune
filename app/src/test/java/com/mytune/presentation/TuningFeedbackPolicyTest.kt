package com.mytune.presentation

import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import com.mytune.domain.tuning.TuningPresets
import org.junit.Test
import org.junit.Assert.*

class TuningFeedbackPolicyTest {
    private fun reading(cents:Double=0.0):TunerUiState {
        val target=TuningPresets.standard.string(6)
        val hz=target.frequency * Math.pow(2.0,cents/1200)
        return TunerUiState(hasPermission=true,microphoneActive=true,readingStatus=ReadingStatus.ACTIVE,
            result=TuningCalculator.calculate(hz,target,.99),rawDetection=PitchDetection(hz,.99,.2,SignalQuality.PITCHED),
            signalQuality=SignalQuality.PITCHED,audioLevel=.6f,lastValidDetectionMs=0)
    }
    @Test fun `success pulses once and boundary jitter never rearms it`() {
        val policy=TuningFeedbackPolicy()
        assertFalse(policy.accept(reading(-20.0),true,0))
        assertTrue(policy.accept(reading(),true,100))
        for (i in 1..20) {
            assertFalse(policy.accept(reading(8.0),true,100+i*200L))
            assertFalse(policy.accept(reading(),true,110+i*200L))
        }
        assertFalse(policy.accept(reading(-20.0),true,5000))
        assertFalse(policy.accept(reading(-20.0),true,5400))
        assertTrue(policy.accept(reading(),true,5500))
        assertFalse(policy.accept(reading(),true,6000))
    }
    @Test fun `no feedback or string vibration for invalid held wrong target or inactive signals`() {
        val base=reading()
        val invalid=listOf(base.copy(rawDetection=PitchDetection()),base.copy(readingStatus=ReadingStatus.HELD),
            base.copy(readingStatus=ReadingStatus.STALE),base.copy(hasPermission=false),base.copy(microphoneActive=false),
            base.copy(error="interrupted"),base.copy(tuningOperationPending=true),base.copy(tuningsLoaded=false),
            base.copy(result=TuningCalculator.calculate(110.0,base.selectedString!!,.99)),
            base.copy(rawDetection=base.rawDetection.copy(confidence=.5)),base.copy(rawDetection=base.rawDetection.copy(rms=.0001)),
            base.copy(rawDetection=base.rawDetection.copy(confidence=1.1)),base.copy(rawDetection=base.rawDetection.copy(frequency=Double.NaN)),
            base.copy(result=base.result!!.copy(cents=Double.NaN)),base.copy(result=base.result.copy(frequency=Double.POSITIVE_INFINITY)),
            base.copy(tuningMode=TuningMode.AUTOMATIC,automaticSelectionStatus=AutomaticSelectionStatus.UNCERTAIN))
        invalid.forEach {state->
            assertFalse(state.hasFreshTargetSignal())
            assertEquals(0f,state.stringVibrationLevel(true),0f)
            assertFalse(TuningFeedbackPolicy().accept(state,true,0))
        }
        assertEquals(.6f,base.stringVibrationLevel(true),0f)
        assertEquals(0f,base.stringVibrationLevel(false),0f)
        assertFalse(TuningFeedbackPolicy().accept(base,false,0))
    }
    @Test fun `silence new target and changed tuning start new cycles but short interruptions do not`() {
        val policy=TuningFeedbackPolicy();val base=reading()
        assertTrue(policy.accept(base,true,0))
        val silent=base.copy(readingStatus=ReadingStatus.HELD,rawDetection=PitchDetection())
        assertFalse(policy.accept(silent,true,100))
        assertFalse(policy.accept(base,true,300))
        assertFalse(policy.accept(silent,true,400))
        assertTrue(policy.accept(base,true,2300))
        val target=TuningPresets.standard.string(5)
        val other=base.copy(selectedString=target,result=TuningCalculator.calculate(target.frequency,target,.99),
            rawDetection=PitchDetection(target.frequency,.99,.2,SignalQuality.PITCHED))
        assertTrue(policy.accept(other,true,4000))
        val drop=TuningPresets.all.first{it.id=="drop-c"}
        val c=drop.string(6)
        assertTrue(policy.accept(base.copy(activeTuning=drop,selectedString=c,
            result=TuningCalculator.calculate(c.frequency,c,.99),rawDetection=PitchDetection(c.frequency,.99,.2,SignalQuality.PITCHED)),true,6000))
    }
    @Test fun `enable is not success and cooldown prevents rapid target changes from buzzing`() {
        val policy=TuningFeedbackPolicy();val base=reading()
        assertFalse(policy.accept(base,false,0));assertFalse(policy.accept(base,true,100))
        assertFalse(policy.accept(reading(-20.0),true,200));assertFalse(policy.accept(reading(-20.0),true,600))
        assertTrue(policy.accept(base,true,700))
        val automatic=base.copy(tuningMode=TuningMode.AUTOMATIC,automaticSelectionStatus=AutomaticSelectionStatus.TRACKING)
        assertFalse(policy.accept(automatic,true,800))
        assertTrue(policy.accept(automatic,true,2300))
    }
}
