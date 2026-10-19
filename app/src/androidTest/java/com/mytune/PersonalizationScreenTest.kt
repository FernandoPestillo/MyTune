package com.mytune

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStoreFactory
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mytune.data.audio.AudioCaptureSource
import com.mytune.data.preferences.*
import com.mytune.data.tuning.*
import com.mytune.domain.audio.*
import com.mytune.domain.model.*
import com.mytune.presentation.*
import com.mytune.ui.components.*
import com.mytune.ui.screens.TunerScreen
import com.mytune.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class PersonalizationScreenTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val owner=ViewModelStore()
    private val clock=AtomicLong(0)
    private lateinit var tuner:TunerViewModel
    private lateinit var appearance:PreferencesViewModel
    private lateinit var repository:PreferencesRepository
    private val files=mutableListOf<java.io.File>()
    private var lightPalette=false
    private class Source:AudioCaptureSource {
        var starts=0
        var callback:((FloatArray,Int)->Unit)?=null
        override suspend fun capture(onStarted:(Int)->Unit,onSamples:(FloatArray,Int)->Unit) {
            starts++;callback=onSamples
            try{onStarted(44100);awaitCancellation()}finally{callback=null}
        }
    }
    private val source=Source()
    private fun file()=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        .resolve("appearance-test-${UUID.randomUUID()}.store").also{files+=it}
    @Before fun setup() {
        val p=file();repository=PreferencesRepository(DataStoreFactory.create(AppPreferencesSerializer,scope=scope,produceFile={p}))
        val t=file();val tunings=TuningRepository(DataStoreFactory.create(TuningSettingsSerializer,scope=scope,produceFile={t}))
        runBlocking {tunings.select("drop-c")}
        val factory=object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>):T = when(modelClass){
                PreferencesViewModel::class.java->PreferencesViewModel(repository)
                else->TunerViewModel(source,PitchDetector{pcm,_->PitchDetection(pcm[0].toDouble(),.99,.2,SignalQuality.PITCHED)},
                    nowMs=clock::get,tuningRepository=tunings)
            } as T
        }
        tuner=ViewModelProvider(owner,factory)[TunerViewModel::class.java]
        appearance=ViewModelProvider(owner,factory)[PreferencesViewModel::class.java]
    }
    private fun content(width:Int=400,height:Int=850,font:Float=1f) {
        compose.setContent {
            val state by tuner.uiState.collectAsState()
            val preferences by appearance.state.collectAsState()
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,font),LocalMotionEnabled provides false) {
                Box(Modifier.requiredSize(width.dp,height.dp)) {
                    MyTuneTheme(preferences.preferences.theme) {
                        val background=MaterialTheme.colorScheme.background
                        SideEffect {lightPalette=MaterialPaletteHolder.isLight(background)}
                        TunerScreen(state,tuner::selectString,{},onModeChange=tuner::setTuningMode,
                            preferencesState=preferences,onHeadstockChange={appearance.headstock(it)},
                            onThemeChange={appearance.theme(it)},onHapticsChange={appearance.haptics(it)},
                            onAnimationChange={appearance.stringAnimation(it)})
                    }
                }
            }
        }
        compose.runOnIdle {tuner.start(true)}
        compose.waitUntil{tuner.uiState.value.tuningsLoaded&&appearance.state.value.loaded&&source.callback!=null}
    }
    @After fun cleanup() {
        compose.runOnIdle {tuner.stop();owner.clear()}
        runBlocking{scope.cancel();scope.coroutineContext.job.join()};files.forEach{it.delete()}
    }
    private fun openSettings(){compose.onNodeWithTag("openSettings").performScrollTo().performClick()}
    private fun setting(tag:String){
        compose.onNodeWithTag("settingsSheet").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil{!appearance.state.value.saving};compose.waitForIdle()
    }
    private fun choose(type:HeadstockType){openSettings();setting("headstock_${type.name}");compose.onNodeWithText("Fechar").performClick()}
    private fun emit(hz:Double,count:Int=1){repeat(count){compose.runOnIdle{
        clock.addAndGet(46);source.callback!!(floatArrayOf(hz.toFloat()),44100)
    };compose.waitForIdle()}}

    @Test fun sixStringsSelectInBothModelsAndSwitchPreservesAutomaticPitchAndCapture(){
        content()
        for(type in HeadstockType.entries){
            choose(type)
            for(string in tuner.uiState.value.activeTuning.strings){
                compose.onNodeWithContentDescription("${string.number}ª corda, ${string.label}")
                    .performScrollTo().performClick().assertIsSelected()
                assertEquals(string,tuner.uiState.value.selectedString)
            }
        }
        compose.onNodeWithTag("mode_AUTOMATIC").performScrollTo().performClick()
        emit(65.406391,4)
        val before=tuner.uiState.value
        assertEquals(6,before.selectedString!!.number)
        choose(HeadstockType.THREE_PER_SIDE)
        assertEquals(before.activeTuning,tuner.uiState.value.activeTuning)
        assertEquals(before.result,tuner.uiState.value.result)
        assertEquals(TuningMode.AUTOMATIC,tuner.uiState.value.tuningMode)
        compose.onNodeWithTag("guitarString_6").assertIsSelected()
        assertEquals(1,source.starts)
    }
    @Test fun settingsPersistThemeAndTogglesWithoutChangingTuning(){
        content();openSettings();setting("theme_LIGHT")
        compose.runOnIdle{assertTrue(lightPalette)}
        setting("hapticsSwitch");setting("animationSwitch");setting("headstock_SIX_INLINE")
        val saved=runBlocking{repository.preferences.first()}
        assertEquals(AppPreferences(HeadstockType.SIX_INLINE,ThemeMode.LIGHT,false,false),saved)
        compose.onNodeWithTag("hapticsSwitch").assertIsOff()
        compose.onNodeWithTag("animationSwitch").assertIsOff()
        setting("theme_SYSTEM")
        assertEquals(ThemeMode.SYSTEM,runBlocking{repository.preferences.first()}.theme)
        compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithText("Afinação: Drop C").assertExists()
        assertEquals(1,source.starts)
    }
    @Test fun largeFontInlineControlsHaveAdequateNonOverlappingTargetsInBothThemes() = assertInlineControls(320,640,1.5f)
    @Test fun landscapeInlineControlsRemainSelectableInBothThemes() = assertInlineControls(850,400,1f)
    private fun assertInlineControls(width:Int,height:Int,font:Float){
        content(width,height,font)
        for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)){
            compose.runOnIdle{appearance.theme(theme);appearance.headstock(HeadstockType.SIX_INLINE)}
            compose.waitUntil{appearance.state.value.preferences.theme==theme&&appearance.state.value.preferences.headstock==HeadstockType.SIX_INLINE}
            compose.waitForIdle()
            val bounds=mutableListOf<androidx.compose.ui.unit.DpRect>()
            for(number in 6 downTo 1){
                val node=compose.onNodeWithTag("guitarString_$number").performScrollTo()
                node.assertIsDisplayed().performClick().assertIsSelected()
                val b=node.getUnclippedBoundsInRoot();assertTrue(b.width>=48.dp);assertTrue(b.height>=48.dp)
                // Screen scroll changes root coordinates; compare positions relative to guitar section.
                val section=compose.onNodeWithTag("guitarSection").getUnclippedBoundsInRoot()
                val relative=androidx.compose.ui.unit.DpRect(b.left,b.top-section.top,b.right,b.bottom-section.top)
                bounds+=relative
                val layouts=mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(tuner.uiState.value.activeTuning.string(number).label)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                assertTrue("number=$number bounds=$b layouts="+layouts.joinToString { "${it.layoutInput.text.text}: size=${it.size} constraints=${it.layoutInput.constraints} overflow=${it.hasVisualOverflow} width=${it.didOverflowWidth} height=${it.didOverflowHeight} paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}" },layouts.none{it.hasVisualOverflow})
            }
            for(a in bounds.indices)for(b in a+1 until bounds.size){
                assertTrue("Controls overlap: ${bounds[a]} ${bounds[b]}",bounds[a].bottom<=bounds[b].top||bounds[b].bottom<=bounds[a].top)
            }
        }
    }
}
private object MaterialPaletteHolder {
    fun isLight(color:androidx.compose.ui.graphics.Color)=color==LightColors.background
}
