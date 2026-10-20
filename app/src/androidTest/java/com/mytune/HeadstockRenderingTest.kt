package com.mytune

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mytune.domain.model.*
import com.mytune.domain.tuning.TuningPresets
import com.mytune.ui.components.*
import com.mytune.ui.components.headstock.*
import com.mytune.ui.theme.MyTuneTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadstockRenderingTest {
    @get:Rule val compose=createComposeRule()
    @Test fun comparisonShowsBlackMapleAndBlackFinishesAndExportsProductionCanvas(){
        var density=1f
        compose.setContent {
            density=LocalDensity.current.density
            MyTuneTheme { HeadstockComparison(Modifier.widthIn(max=780.dp).fillMaxWidth().height(440.dp)) }
        }
        compose.waitForIdle()
        val samples=mapOf(HeadstockType.THREE_PER_SIDE to RelativePoint(.5f,.18f),
            HeadstockType.SIX_INLINE to RelativePoint(.76f,.15f),
            HeadstockType.SIX_INLINE_REVERSE to RelativePoint(.32f,.60f))
        for((type,sample)in samples){
            val bitmap=compose.onNodeWithTag("preview_${type.name}").assertIsDisplayed().captureToImage()
            val viewport=HeadstockViewport.fit(HeadstockGeometry.forType(type),bitmap.width/density,bitmap.height/density)
            val point=viewport.map(sample)
            val luminance=bitmap.toPixelMap()[(point.x*density).toInt(),(point.y*density).toInt()].luminance()
            if(type==HeadstockType.SIX_INLINE) assertTrue("Maple must remain light",luminance>.35f)
            else assertTrue("Black finish: $type",luminance<.15f)
        }
        val output=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("headstock-review")!!
            .resolve("comparison.png")
        output.outputStream().use { stream ->
            assertTrue(compose.onNodeWithTag("headstockComparison").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream))
        }
    }
    @Test fun actualPostTouchesResolveLogicalStringsForThreeModelsAndTwoWidths(){
        var model by mutableStateOf(HeadstockType.THREE_PER_SIDE)
        var width by mutableFloatStateOf(272f)
        val strings=TuningPresets.all.first{it.id=="drop-c"}.strings
        var selected by mutableStateOf<GuitarStringNote?>(null)
        var density=1f
        compose.setContent {
            density=LocalDensity.current.density
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                MyTuneTheme {
                    val height=HeadstockGeometry.forType(model).minimumHeight(58f)
                    GuitarView(selected,{selected=it},Modifier.width(width.dp).height(height.dp),
                        strings=strings,headstock=model,animationsEnabled=false)
                }
            }
        }
        for(type in HeadstockType.entries)for(w in listOf(224f,360f)){
            compose.runOnIdle{model=type;width=w};compose.waitForIdle()
            val g=HeadstockGeometry.forType(type)
            val v=HeadstockViewport.fit(g,w,g.minimumHeight(58f),if(type==HeadstockType.THREE_PER_SIDE)66f else 82f)
            for(route in g.routes){
                val peg=v.map(route.peg)
                compose.onNodeWithTag("headstockCanvas").performTouchInput {click(Offset(peg.x*density,peg.y*density))}
                compose.onNodeWithTag("guitarString_${route.number}").assertIsSelected()
                compose.runOnIdle{assertEquals(strings.first{it.number==route.number},selected)}
            }
        }
    }
}
