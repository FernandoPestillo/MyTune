package com.mytune.ui

import com.mytune.domain.model.*
import com.mytune.ui.components.headstock.*
import com.mytune.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class HeadstockGeometryTest {
    @Test fun `both models preserve physical identities and routes stay inside the drawing`() {
        HeadstockType.entries.forEach { type ->
            val g = HeadstockGeometry.forType(type)
            assertEquals((6 downTo 1).toList(), g.routes.map { it.number })
            g.routes.forEach { route ->
                listOf(route.peg, route.button, route.nut, route.end).forEach {
                    assertTrue("$type $it", it.x in 0f..1f && it.y in 0f..1f)
                }
                assertEquals(.375f + (6 - route.number) * .05f, route.nut.x, .0001f)
                assertEquals(route.nut.x, route.end.x, 0f)
                assertTrue(route.peg.y < g.nutY)
            }
            if (type == HeadstockType.THREE_PER_SIDE) {
                assertEquals(3, g.routes.count { it.button.x == 0f })
                assertEquals(3, g.routes.count { it.button.x == 1f })
            } else {
                assertEquals(1, g.routes.map { it.peg.x }.distinct().size)
                assertEquals(6, g.routes.count { it.button.x == 0f })
                assertTrue(g.routes.zipWithNext().all { (a,b) -> a.peg.y > b.peg.y })
            }
        }
    }
    @Test fun `48 pixel peg areas fit and do not overlap at supported component sizes`() {
        HeadstockType.entries.forEach { type ->
            for (width in listOf(224f,272f,400f,700f)) for (height in listOf(360f,480f,600f)) {
                val g = HeadstockGeometry.forType(type)
                g.routes.forEach { r ->
                    assertEquals(r.number, g.pegAt(r.peg.x,r.peg.y,24/width,24/height))
                    assertTrue(r.peg.x * width - 24 >= 0 && r.peg.x * width + 24 <= width)
                    assertTrue(r.peg.y * height - 24 >= 0 && r.peg.y * height + 24 <= height)
                }
                g.routes.forEachIndexed { index, a -> g.routes.drop(index+1).forEach { b ->
                    assertTrue(abs(a.peg.x-b.peg.x)*width >= 48 || abs(a.peg.y-b.peg.y)*height >= 48)
                } }
                assertNull(g.pegAt(0f,1f,24/width,24/height))
            }
        }
    }
    @Test fun `themes follow explicit or system choice and text colors maintain contrast`() {
        assertTrue(usesDarkTheme(ThemeMode.DARK,false)); assertFalse(usesDarkTheme(ThemeMode.LIGHT,true))
        assertTrue(usesDarkTheme(ThemeMode.SYSTEM,true)); assertFalse(usesDarkTheme(ThemeMode.SYSTEM,false))
        fun contrast(a:Color,b:Color):Float {
            val x=a.luminance();val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        for (scheme in listOf(DarkColors,LightColors)) {
            for (bg in listOf(scheme.background,scheme.surface)) {
                for (fg in listOf(scheme.onSurface,scheme.onSurfaceVariant,scheme.primary,scheme.secondary,scheme.error))
                    assertTrue("$fg vs $bg",contrast(fg,bg)>=4.5f)
            }
            val selected=scheme.primary.copy(alpha=.08f).compositeOver(scheme.surface)
            assertTrue(contrast(scheme.primary,selected)>=4.5f)
            val retained=scheme.onSurfaceVariant.copy(alpha=.95f).compositeOver(scheme.surface)
            assertTrue(contrast(retained,scheme.surface)>=4.5f)
            assertTrue(contrast(scheme.primary,scheme.onPrimary)>=4.5f)
        }
    }
}
