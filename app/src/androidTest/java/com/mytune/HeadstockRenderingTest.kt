package com.mytune

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
