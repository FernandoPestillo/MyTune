package com.mytune.ui.components.headstock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.mytune.domain.model.HeadstockType
import com.mytune.ui.theme.*

/** Shared renderer; all construction differences are supplied by the model's definition. */
@Composable
internal fun HeadstockDrawing(type: HeadstockType, emphasis: Map<Int,State<Float>>, selected: Int?,
    wave: State<Float>, controlWidth: Float = 0f, controlHeight: Float = 0f) {
    val g=remember(type) { HeadstockGeometry.forType(type) }
    val head=remember(g) { g.toPath() }
    val light=g.finish==HeadstockFinish.MAPLE
    val edge=when(g.finish) {
        HeadstockFinish.MAPLE->InstrumentColors.mapleEdge
        HeadstockFinish.GRAPHITE->InstrumentColors.graphiteEdge
        else->InstrumentColors.woodEdge
    }
    val middle=when(g.finish) {
        HeadstockFinish.MAPLE->InstrumentColors.maple
        HeadstockFinish.GRAPHITE->InstrumentColors.graphite
        else->InstrumentColors.wood
    }
    val outline=when(g.finish) {
        HeadstockFinish.MAPLE->InstrumentColors.mapleEdge
        HeadstockFinish.GRAPHITE->InstrumentColors.graphiteOutline
        else->InstrumentColors.woodOutline
    }
    val accent=if(light) LightColors.primary else DarkColors.primary
    val guide=Muted.copy(alpha=.30f)
    Canvas(Modifier.fillMaxSize()) {
        val pixelsPerDp=density
        val width=size.width/pixelsPerDp;val height=size.height/pixelsPerDp
        val v=HeadstockViewport.fit(g,width,height,controlWidth)
        val centers=if(controlWidth>0) v.controlCenters(g,height,controlHeight) else emptyMap()
        // Work in dp first, then apply one uniform model scale. No independent X/Y stretching.
        withTransform({scale(pixelsPerDp,pixelsPerDp,pivot=Offset.Zero)}) {
            if(controlWidth>0) g.routes.forEach { route ->
                val anchor=v.map(route.knob)
                val fromX=if(route.side==TunerSide.LEFT) controlWidth+2 else width-controlWidth-2
                val toX=anchor.x+route.side.direction*8*v.scale
                drawLine(guide,Offset(fromX,centers.getValue(route.number)),Offset(toX,anchor.y),.65f)
            }
            withTransform({translate(v.originX,v.originY);scale(v.scale,v.scale,pivot=Offset.Zero)}) {
                fun p(point: RelativePoint)=Offset(point.x*g.designWidth,point.y*g.designHeight)
                fun at(x:Float,y:Float)=Offset(x*g.designWidth,y*g.designHeight)
                withTransform({translate(1.3f,1.8f)}) {drawPath(head,Color.Black.copy(alpha=.15f))}
                drawPath(head,Brush.horizontalGradient(
                    listOf(edge,middle,if(light) InstrumentColors.mapleLight else middle,edge),
                    startX=g.bounds.left*g.designWidth,endX=g.bounds.right*g.designWidth))
                drawPath(head,outline,style=Stroke(1.25f))
                clipPath(head) {
                    // Quiet grain and edge highlights, without inscriptions or proprietary decoration.
                    if(g.finish!=HeadstockFinish.GRAPHITE) for(i in 0..16) {
                        val x=.18f+i*.04f
                        val grain=Path().apply {
                            moveTo(x*g.designWidth,.03f*g.designHeight)
                            cubicTo((x+.015f)*g.designWidth,.35f*g.designHeight,
                                (x-.012f)*g.designWidth,.65f*g.designHeight,x*g.designWidth,g.designHeight)
                        }
                        drawPath(grain,(if(light) InstrumentColors.mapleGrain else InstrumentColors.grain)
                            .copy(alpha=if(light) .10f else .045f),style=Stroke(.55f))
                    }
                    drawLine(outline.copy(alpha=.15f),at(.36f,0f),at(.36f,1f),1.2f)
                }
                val neckTop=g.nutY
                drawRect(InstrumentColors.fretboard,at(.345f,neckTop),Size(g.designWidth*.31f,g.designHeight*(1-neckTop)))
                for(fraction in listOf(.30f,.62f,.92f)) {
                    val y=neckTop+(1-neckTop)*fraction
                    drawLine(InstrumentColors.metalDark,at(.345f,y+.004f),at(.655f,y+.004f),2.4f)
                    drawLine(InstrumentColors.steelMid,at(.345f,y),at(.655f,y),1.9f)
                }
                drawCircle(InstrumentColors.fret,2.5f,at(.5f,neckTop+(1-neckTop)*.46f))
                drawLine(InstrumentColors.nut,at(.334f,neckTop),at(.666f,neckTop),5.2f)
                g.routes.forEach { route ->
                    val post=p(route.peg);val knob=p(route.knob)
                    // Axle, bevelled button, washer and split post remain distinguishable at preview scale.
                    drawLine(InstrumentColors.metalDark,post+Offset(0f,1.5f),knob+Offset(0f,1.5f),5.4f)
                    drawLine(InstrumentColors.steelMid,post,knob,3.7f)
                    drawRoundRect(InstrumentColors.metalDark,knob-Offset(8.5f,10f),Size(17f,23f),CornerRadius(5f))
                    drawRoundRect(Brush.horizontalGradient(
                        listOf(InstrumentColors.metalDark,InstrumentColors.steelMid,InstrumentColors.steelLight,InstrumentColors.metalDark),
                        startX=knob.x-8,endX=knob.x+8),
                        knob-Offset(8f,11f),Size(16f,22f),CornerRadius(5f))
                    drawLine(InstrumentColors.steelLight.copy(alpha=.65f),knob-Offset(4f,7.5f),knob+Offset(4f,-7.5f),.9f)
                    drawCircle(InstrumentColors.peg,8f,post+Offset(.5f,1f))
                    drawCircle(InstrumentColors.steelMid,6.6f,post)
                    drawCircle(InstrumentColors.steelLight,4.6f,post-Offset(.6f,.7f))
                    drawCircle(InstrumentColors.metalDark,3.4f,post)
                    val strength=emphasis[route.number]?.value?:0f
                    val base=if(light) InstrumentColors.darkString else InstrumentColors.string
                    val color=lerp(base.copy(alpha=.85f),accent,strength)
                    val nut=p(route.nut);val end=p(route.end)
                    if(strength>0) drawLine(accent.copy(alpha=.10f*strength),nut,end,8f)
                    // Straight headstock route; only the free span past the nut receives the subtle wave.
                    val displacement=if(selected==route.number) wave.value/v.scale else 0f
                    val string=Path().apply {
                        moveTo(post.x,post.y);lineTo(nut.x,nut.y)
                        quadraticTo(nut.x+displacement,(nut.y+end.y)/2,end.x,end.y)
                    }
                    drawPath(string,color,style=Stroke(route.thickness,cap=StrokeCap.Round))
                    drawLine(InstrumentColors.screwSlot,post-Offset(1.9f,0f),post+Offset(1.9f,0f),.65f)
                }
            }
        }
    }
}

private fun HeadstockGeometry.toPath() = Path().apply {
    fun x(v:Float)=v*designWidth
    fun y(v:Float)=v*designHeight
    outline.forEach { c -> when(c) {
        is OutlineCommand.Move->moveTo(x(c.p.x),y(c.p.y))
        is OutlineCommand.Line->lineTo(x(c.p.x),y(c.p.y))
        is OutlineCommand.Quad->quadraticTo(x(c.control.x),y(c.control.y),x(c.end.x),y(c.end.y))
        is OutlineCommand.Cubic->cubicTo(x(c.first.x),y(c.first.y),x(c.second.x),y(c.second.y),x(c.end.x),y(c.end.y))
        OutlineCommand.Close->close()
    } }
}
