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
    @Test fun `three silhouettes map every physical string to its conventional post`() {
        val three=HeadstockGeometry.forType(HeadstockType.THREE_PER_SIDE)
        val normal=HeadstockGeometry.forType(HeadstockType.SIX_INLINE)
        val reverse=HeadstockGeometry.forType(HeadstockType.SIX_INLINE_REVERSE)
        for(g in listOf(three,normal,reverse)) {
            assertEquals((6 downTo 1).toList(),g.routes.map {it.number})
            assertEquals(6,g.routes.map {it.peg}.distinct().size)
            g.routes.forEach {r->
                assertTrue(r.peg.y<g.nutY)
                assertEquals(r.number,g.neckStringAt(r.nut.x,.95f))
                assertEquals(r.number,g.copy(routes=g.routes.reversed()).neckStringAt(r.nut.x,.95f))
                assertEquals(r.number,g.pegAt(r.peg.x,r.peg.y,.07f,.04f))
                assertTrue(contains(g,r.peg))
                assertTrue(if(r.side==TunerSide.LEFT) r.knob.x<r.peg.x else r.knob.x>r.peg.x)
            }
            assertTrue(g.routes.zipWithNext().all {(a,b)->a.thickness>b.thickness})
            // Intersecting headstock string segments indicate incorrect routing, not a pitch ambiguity.
            g.routes.forEachIndexed {i,a->g.routes.drop(i+1).forEach {b->assertFalse("${g.type}: ${a.number}/${b.number}",cross(a.peg,a.nut,b.peg,b.nut))}}
        }
        assertEquals(listOf(6,5,4),three.routes.filter {it.side==TunerSide.LEFT}.sortedByDescending {it.peg.y}.map {it.number})
        assertEquals(listOf(3,2,1),three.routes.filter {it.side==TunerSide.RIGHT}.sortedBy {it.peg.y}.map {it.number})
        assertEquals((6 downTo 1).toList(),normal.routes.sortedByDescending {it.peg.y}.map {it.number})
        assertTrue(normal.routes.all {it.side==TunerSide.LEFT})
        assertEquals((1..6).toList(),reverse.routes.sortedByDescending {it.peg.y}.map {it.number})
        assertTrue(reverse.routes.all {it.side==TunerSide.RIGHT})
        assertNotEquals(normal.outline,reverse.outline)
        assertTrue(normal.outline.any {it is OutlineCommand.Cubic})
        // The reverse model keeps angular right edges and the reference's long curved left side.
        assertTrue(reverse.outline.any {it is OutlineCommand.Cubic})
        assertTrue(reverse.outline.any {it is OutlineCommand.Line})
    }
    @Test fun `uniform fit keeps hardware inside and accessible controls separated at different sizes`() {
        for(type in HeadstockType.entries) for(width in listOf(224f,272f,400f,700f)) for(control in listOf(58f,66f)) {
            val g=HeadstockGeometry.forType(type)
            val height=g.minimumHeight(control)
            val buttonWidth=if(type==HeadstockType.THREE_PER_SIDE) 83f else 90f
            val v=HeadstockViewport.fit(g,width,height,buttonWidth)
            val centers=v.controlCenters(g,height,control)
            g.routes.forEach {r->
                val post=v.map(r.peg);val knob=v.map(r.knob)
                for(p in listOf(post,knob)) assertTrue("$type $width $p",p.x in 0f..width && p.y in 0f..height)
                // Include washer radius and the rotated paddle, not just their center points.
                for((center,radius)in listOf(r.peg to 15f,r.knob to 23f)) {
                    val p=v.map(center)
                    assertTrue("Clipped hardware: $type $width",p.x-radius*v.scale>=0 && p.x+radius*v.scale<=width &&
                        p.y-radius*v.scale>=0 && p.y+radius*v.scale<=height)
                }
                assertEquals(r.peg.x,v.inverse(post).x,.00001f);assertEquals(r.peg.y,v.inverse(post).y,.00001f)
                assertTrue(centers.getValue(r.number)-control/2>=0)
                assertTrue(centers.getValue(r.number)+control/2<=height)
                val x=v.map(RelativePoint(r.peg.x+.01f,r.peg.y))
                val y=v.map(RelativePoint(r.peg.x,r.peg.y+.01f))
                assertEquals((x.x-post.x)/g.designWidth,(y.y-post.y)/g.designHeight,.00001f)
            }
            TunerSide.entries.forEach {side->
                val rows=g.routes.filter{it.side==side}.map {centers.getValue(it.number)}.sorted()
                assertTrue(rows.zipWithNext().all {(a,b)->b-a>=control+3.99f})
            }
            // Preview and main view use different scales, but exactly the same physical aspect ratio.
            val preview=HeadstockViewport.fit(g,100f,140f)
            val a=preview.map(RelativePoint(.35f,g.nutY));val b=preview.map(RelativePoint(.65f,1f))
            val c=v.map(RelativePoint(.35f,g.nutY));val d=v.map(RelativePoint(.65f,1f))
            assertEquals((b.x-a.x)/(b.y-a.y),(d.x-c.x)/(d.y-c.y),.00001f)
        }
    }
    private fun cross(a:RelativePoint,b:RelativePoint,c:RelativePoint,d:RelativePoint):Boolean {
        fun turn(p:RelativePoint,q:RelativePoint,r:RelativePoint)=(q.x-p.x)*(r.y-p.y)-(q.y-p.y)*(r.x-p.x)
        return turn(a,b,c)*turn(a,b,d)<0 && turn(c,d,a)*turn(c,d,b)<0
    }
    private fun contains(g:HeadstockGeometry,p:RelativePoint):Boolean {
        val points=mutableListOf<RelativePoint>();var current=RelativePoint(0f,0f)
        for(c in g.outline) {
            when(c) {
                is OutlineCommand.Move->{current=c.p;points+=current}
                is OutlineCommand.Line->{current=c.p;points+=current}
                is OutlineCommand.Quad->{val start=current;for(i in 1..30){val t=i/30f;val u=1-t
                    points+=RelativePoint(u*u*start.x+2*u*t*c.control.x+t*t*c.end.x,u*u*start.y+2*u*t*c.control.y+t*t*c.end.y)};current=c.end}
                is OutlineCommand.Cubic->{val start=current;for(i in 1..30){val t=i/30f;val u=1-t
                    points+=RelativePoint(u*u*u*start.x+3*u*u*t*c.first.x+3*u*t*t*c.second.x+t*t*t*c.end.x,
                        u*u*u*start.y+3*u*u*t*c.first.y+3*u*t*t*c.second.y+t*t*t*c.end.y)};current=c.end}
                OutlineCommand.Close->Unit
            }
        }
        var inside=false
        for(i in points.indices) {val a=points[i];val b=points[(i+1)%points.size]
            if((a.y>p.y)!=(b.y>p.y) && p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x)inside=!inside}
        return inside
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
