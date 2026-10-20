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

/** The existing three definitions share one renderer; audio state only changes emphasis and wave. */
@Composable
internal fun HeadstockDrawing(type: HeadstockType, emphasis: Map<Int, State<Float>>, selected: Int?,
    wave: State<Float>, controlWidth: Float = 0f, controlHeight: Float = 0f) {
    val g = remember(type) { HeadstockGeometry.forType(type) }
    // Silhouette, grain, materials and hardware brushes survive frequency/animation updates.
    val art = remember(g) { HeadstockArtwork(g) }
    val accent = if (g.finish == HeadstockFinish.MAPLE) LightColors.primary else DarkColors.primary
    val guide = Muted.copy(alpha = .30f)
    Canvas(Modifier.fillMaxSize()) {
        val pixelsPerDp = density
        val width = size.width / pixelsPerDp
        val height = size.height / pixelsPerDp
        val v = HeadstockViewport.fit(g, width, height, controlWidth)
        val centers = if (controlWidth > 0) v.controlCenters(g, height, controlHeight) else emptyMap()
        withTransform({ scale(pixelsPerDp, pixelsPerDp, pivot = Offset.Zero) }) {
            if (controlWidth > 0) g.routes.forEach { route ->
                val anchor = v.map(route.knob)
                val fromX = if (route.side == TunerSide.LEFT) controlWidth + 2 else width - controlWidth - 2
                drawLine(guide, Offset(fromX, centers.getValue(route.number)),
                    Offset(anchor.x + route.side.direction * 18 * v.scale, anchor.y), .65f)
            }
            withTransform({ translate(v.originX, v.originY); scale(v.scale, v.scale, pivot = Offset.Zero) }) {
                withTransform({ translate(1.8f, 2.5f) }) {
                    drawPath(art.head, Color.Black.copy(alpha = .22f))
                }
                drawPath(art.head, art.surface)
                clipPath(art.head) {
                    art.grain.forEachIndexed { index, path ->
                        drawPath(path, art.grainColor.copy(alpha = if (index % 3 == 0) .14f else .07f),
                            style = art.grainStroke)
                    }
                    drawPath(art.reflection, art.sheen)
                }
                drawPath(art.head, art.rim, style = art.edgeStroke)
                if (g.finish == HeadstockFinish.BLACK_GLOSS) {
                    clipRect(bottom = g.nutY * g.designHeight - 3) {
                        drawPath(art.head, InstrumentColors.gold, style = art.bindingEdge)
                        drawPath(art.head, InstrumentColors.binding, style = art.bindingLine)
                    }
                }
                drawNeck(g, art)
                art.hardware.forEach { drawTuner(it, art) }
                art.hardware.forEach { hardware ->
                    val route = hardware.route
                    val strength = emphasis[route.number]?.value ?: 0f
                    val headColor = lerp(art.headString, accent, strength)
                    val neckColor = lerp(InstrumentColors.string, accent, strength)
                    if (strength > 0) {
                        drawLine(accent.copy(alpha = .10f * strength), hardware.nut, hardware.post, 7f)
                        drawLine(accent.copy(alpha = .10f * strength), hardware.nut, hardware.end, 7f)
                    }
                    // Each physical string runs directly to its own post. The headstock span never waves.
                    drawLine(headColor, hardware.nut, hardware.post, route.thickness, StrokeCap.Round)
                    val displacement = if (selected == route.number) wave.value / v.scale else 0f
                    if (displacement == 0f) {
                        drawLine(neckColor, hardware.nut, hardware.end, route.thickness, StrokeCap.Round)
                    } else {
                        hardware.freeString.apply {
                            reset(); moveTo(hardware.nut.x, hardware.nut.y)
                            quadraticTo(hardware.nut.x + displacement, (hardware.nut.y + hardware.end.y) / 2,
                                hardware.end.x, hardware.end.y)
                        }
                        drawPath(hardware.freeString, neckColor, style = hardware.stringStroke)
                    }
                    // A short split in the crown remains visible without interrupting the string route.
                    drawLine(art.metal.dark, hardware.post - Offset(2f, 2f), hardware.post + Offset(2f, -2f), .8f)
                }
            }
        }
    }
}

private data class MetalFinish(val dark: Color, val mid: Color, val light: Color)
private class HardwareArtwork(val route: StringRoute, private val g: HeadstockGeometry, metal: MetalFinish) {
    private fun point(p: RelativePoint) = Offset(p.x * g.designWidth, p.y * g.designHeight)
    val post = point(route.peg)
    val knob = point(route.knob)
    val nut = point(route.nut)
    val end = point(route.end)
    val washer = Brush.radialGradient(listOf(metal.light, metal.mid, metal.dark, metal.light),
        center = post - Offset(3f, 4f), radius = 18f)
    val crown = Brush.radialGradient(listOf(metal.light, metal.mid, metal.dark),
        center = post - Offset(2f, 5f), radius = 11f)
    val freeString = Path()
    val stringStroke = Stroke(route.thickness, cap = StrokeCap.Round)
}

/** All graphics below are built once per model, in its original uniform coordinate system. */
private class HeadstockArtwork(g: HeadstockGeometry) {
    val head = g.toPath()
    val glossy = g.finish == HeadstockFinish.BLACK_GLOSS
    val maple = g.finish == HeadstockFinish.MAPLE
    val metal = when (g.finish) {
        HeadstockFinish.BLACK_GLOSS -> MetalFinish(InstrumentColors.goldDark, InstrumentColors.gold, InstrumentColors.goldLight)
        HeadstockFinish.MAPLE -> MetalFinish(InstrumentColors.metalDark, InstrumentColors.steelMid, InstrumentColors.steelLight)
        HeadstockFinish.GRAPHITE -> MetalFinish(InstrumentColors.blackMetal, InstrumentColors.blackMetalMid, InstrumentColors.blackMetalLight)
    }
    val rim = when (g.finish) {
        HeadstockFinish.BLACK_GLOSS -> InstrumentColors.goldDark
        HeadstockFinish.MAPLE -> InstrumentColors.mapleEdge
        HeadstockFinish.GRAPHITE -> InstrumentColors.graphiteOutline
    }
    val surface = Brush.linearGradient(
        colors = when (g.finish) {
            HeadstockFinish.BLACK_GLOSS -> listOf(InstrumentColors.lacquer, InstrumentColors.lacquerReflection,
                InstrumentColors.lacquer, InstrumentColors.lacquerReflection, InstrumentColors.lacquer)
            HeadstockFinish.MAPLE -> listOf(InstrumentColors.mapleEdge, InstrumentColors.maple,
                InstrumentColors.mapleLight, InstrumentColors.maple, InstrumentColors.mapleEdge)
            HeadstockFinish.GRAPHITE -> listOf(InstrumentColors.graphiteEdge, InstrumentColors.graphite,
                InstrumentColors.graphiteEdge)
        }, start = Offset(g.bounds.left * g.designWidth, 0f),
        end = Offset(g.bounds.right * g.designWidth, g.designHeight * .13f))
    val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = if (glossy) .08f else .025f), Color.Transparent),
        start = Offset(g.designWidth * .25f, 0f), end = Offset(g.designWidth * .64f, g.designHeight * .6f))
    val reflection = Path().apply {
        moveTo(g.designWidth * .21f, 0f); lineTo(g.designWidth * .52f, 0f)
        cubicTo(g.designWidth * .43f, g.designHeight * .3f, g.designWidth * .51f, g.designHeight * .6f,
            g.designWidth * .34f, g.designHeight * .82f)
        lineTo(g.designWidth * .17f, g.designHeight * .82f); close()
    }
    val grainColor = InstrumentColors.mapleGrain
    val grain = if (maple) (0..34).map { index ->
        val x = (.16f + index * .024f) * g.designWidth
        val bend = (if (index % 2 == 0) 1 else -1) * (2 + index % 4)
        Path().apply {
            moveTo(x, 0f)
            cubicTo(x + bend, g.designHeight * .25f, x - bend * 1.5f, g.designHeight * .53f,
                x + bend * .5f, g.designHeight * .84f)
        }
    } else emptyList()
    val grainStroke = Stroke(.65f)
    val edgeStroke = Stroke(if (maple) 1.8f else 1.2f)
    val bindingEdge = Stroke(4f)
    val bindingLine = Stroke(1.4f)
    val headString = if (maple) InstrumentColors.darkString else InstrumentColors.string
    val hardware = g.routes.map { HardwareArtwork(it, g, metal) }
    val button = Path().apply {
        moveTo(-13f, -15f); quadraticTo(-7f, -20f, 4f, -18f)
        quadraticTo(15f, -16f, 15f, -7f); lineTo(15f, 7f)
        quadraticTo(15f, 16f, 4f, 18f); quadraticTo(-7f, 20f, -13f, 15f)
        quadraticTo(-17f, 0f, -13f, -15f); close()
    }
    val buttonPaint = Brush.linearGradient(
        listOf(metal.dark, metal.mid, metal.light, metal.mid, metal.dark), Offset(-16f, -18f), Offset(17f, 16f))
    val buttonSheen = Brush.linearGradient(listOf(metal.light.copy(alpha = .60f), Color.Transparent),
        Offset(-12f, -15f), Offset(4f, 11f))
    val buttonEdge = Stroke(.9f)
    val fretboard = Brush.horizontalGradient(listOf(InstrumentColors.fretboard, Color(0xFF302720),
        InstrumentColors.fretboard), startX = .345f * g.designWidth, endX = .655f * g.designWidth)
}

private fun DrawScope.drawNeck(g: HeadstockGeometry, art: HeadstockArtwork) {
    fun at(x: Float, y: Float) = Offset(x * g.designWidth, y * g.designHeight)
    val top = g.nutY
    drawRect(if (g.finish == HeadstockFinish.GRAPHITE) InstrumentColors.lacquer else InstrumentColors.fretboard,
        at(.345f, top), Size(g.designWidth * .31f, g.designHeight * (1 - top)))
    if (g.finish != HeadstockFinish.GRAPHITE) {
        drawRect(art.fretboard, at(.345f, top), Size(g.designWidth * .31f, g.designHeight * (1 - top)))
    }
    for (fraction in listOf(.30f, .62f, .92f)) {
        val y = top + (1 - top) * fraction
        drawLine(InstrumentColors.metalDark, at(.345f, y + .003f), at(.655f, y + .003f), 3.4f)
        drawLine(InstrumentColors.steelMid, at(.345f, y), at(.655f, y), 2.7f)
        drawLine(InstrumentColors.steelLight, at(.345f, y - .001f), at(.655f, y - .001f), .7f)
    }
    drawCircle(InstrumentColors.fret, 2.6f, at(.5f, top + (1 - top) * .46f))
    val darkNut = g.finish == HeadstockFinish.GRAPHITE
    drawLine(if (darkNut) InstrumentColors.metalDark else InstrumentColors.nut, at(.334f, top), at(.666f, top), 7f)
    drawLine(if (darkNut) InstrumentColors.blackMetalLight else InstrumentColors.binding,
        at(.334f, top - .002f), at(.666f, top - .002f), 1.3f)
}

private fun DrawScope.drawTuner(hardware: HardwareArtwork, art: HeadstockArtwork) {
    val post = hardware.post
    val knob = hardware.knob
    val metal = art.metal
    // Axles enter from outside the silhouette; a collar and bevel distinguish each mechanical part.
    drawLine(Color.Black.copy(alpha = .5f), post + Offset(0f, 3f), knob + Offset(0f, 3f), 8f)
    drawLine(metal.dark, post, knob, 7f)
    drawLine(metal.mid, post - Offset(0f, 1f), knob - Offset(0f, 1f), 4.5f)
    drawLine(metal.light.copy(alpha = .65f), post - Offset(0f, 2.6f), knob - Offset(0f, 2.6f), 1f)
    val collar = Offset(knob.x - hardware.route.side.direction * 20, knob.y)
    drawLine(metal.dark, collar - Offset(0f, 5f), collar + Offset(0f, 5f), 5f)
    drawLine(metal.light, collar - Offset(0f, 4.5f), collar + Offset(0f, 4.5f), 1.5f)
    withTransform({
        translate(knob.x, knob.y)
        rotate(if (art.glossy) 0f else hardware.route.side.direction * 12f, pivot = Offset.Zero)
    }) {
        withTransform({ translate(1f, 2f) }) { drawPath(art.button, metal.dark) }
        drawPath(art.button, art.buttonPaint)
        drawPath(art.button, metal.light.copy(alpha = .65f), style = art.buttonEdge)
        clipPath(art.button) {
            drawRect(art.buttonSheen, Offset(-17f, -20f), Size(34f, 20f))
            drawLine(metal.dark.copy(alpha = .4f), Offset(-15f, 7f), Offset(16f, 7f), .65f)
        }
    }
    drawCircle(Color.Black.copy(alpha = .45f), 14.3f, post + Offset(1f, 2f))
    drawCircle(hardware.washer, 13f, post)
    drawCircle(metal.dark, 10.2f, post)
    drawCircle(metal.light, 9.6f, post - Offset(.4f, .6f))
    drawCircle(hardware.crown, 8.2f, post)
    drawCircle(metal.dark, 5.4f, post - Offset(0f, 2f))
    drawCircle(hardware.crown, 4.9f, post - Offset(0f, 3.2f))
    drawCircle(metal.light.copy(alpha = .85f), 1.9f, post - Offset(1.7f, 5.1f))
}

private fun HeadstockGeometry.toPath() = Path().apply {
    fun x(v: Float) = v * designWidth
    fun y(v: Float) = v * designHeight
    outline.forEach { c -> when (c) {
        is OutlineCommand.Move -> moveTo(x(c.p.x), y(c.p.y))
        is OutlineCommand.Line -> lineTo(x(c.p.x), y(c.p.y))
        is OutlineCommand.Quad -> quadraticTo(x(c.control.x), y(c.control.y), x(c.end.x), y(c.end.y))
        is OutlineCommand.Cubic -> cubicTo(x(c.first.x), y(c.first.y), x(c.second.x), y(c.second.y), x(c.end.x), y(c.end.y))
        OutlineCommand.Close -> close()
    } }
}
