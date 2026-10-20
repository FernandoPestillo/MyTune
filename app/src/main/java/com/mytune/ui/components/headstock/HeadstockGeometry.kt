package com.mytune.ui.components.headstock

import com.mytune.domain.model.HeadstockType
import kotlin.math.abs

data class RelativePoint(val x: Float, val y: Float)
data class RelativeBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
enum class TunerSide(val direction: Float) { LEFT(-1f), RIGHT(1f) }
enum class HeadstockFinish { DARK_WOOD, MAPLE, GRAPHITE }

sealed interface OutlineCommand {
    data class Move(val p: RelativePoint) : OutlineCommand
    data class Line(val p: RelativePoint) : OutlineCommand
    data class Quad(val control: RelativePoint, val end: RelativePoint) : OutlineCommand
    data class Cubic(val first: RelativePoint, val second: RelativePoint, val end: RelativePoint) : OutlineCommand
    data object Close : OutlineCommand
}

private class Outline {
    val commands = mutableListOf<OutlineCommand>()
    fun move(x: Float, y: Float) { commands += OutlineCommand.Move(RelativePoint(x,y)) }
    fun line(x: Float, y: Float) { commands += OutlineCommand.Line(RelativePoint(x,y)) }
    fun quad(x: Float, y: Float, endX: Float, endY: Float) { commands += OutlineCommand.Quad(RelativePoint(x,y),RelativePoint(endX,endY)) }
    fun cubic(x: Float,y: Float,x2: Float,y2: Float,endX: Float,endY: Float) {
        commands += OutlineCommand.Cubic(RelativePoint(x,y),RelativePoint(x2,y2),RelativePoint(endX,endY))
    }
    fun close() { commands += OutlineCommand.Close }
}
private fun outline(block: Outline.() -> Unit) = Outline().apply(block).commands.toList()

data class StringRoute(val number: Int, val peg: RelativePoint, val knob: RelativePoint,
    val side: TunerSide, val nut: RelativePoint, val end: RelativePoint) {
    // Thickness belongs to physical identity, never to rendering/list order.
    val thickness: Float get() = when (number) { 6 -> 2.1f; 5 -> 1.85f; 4 -> 1.6f; 3 -> 1.35f; 2 -> 1.1f; else -> .85f }
}

/** Every definition owns its outline, hardware, scale and material. All coordinates are normalized. */
data class HeadstockGeometry(val type: HeadstockType, val designWidth: Float, val designHeight: Float,
    val outline: List<OutlineCommand>, val bounds: RelativeBounds, val finish: HeadstockFinish,
    val routes: List<StringRoute>, val nutY: Float) {
    fun pegAt(x: Float, y: Float, radiusX: Float, radiusY: Float): Int? = routes
        .filter { abs(it.peg.x-x) <= radiusX && abs(it.peg.y-y) <= radiusY }
        .minByOrNull { abs(it.peg.x-x)/radiusX + abs(it.peg.y-y)/radiusY }?.number

    fun neckStringAt(x: Float, y: Float): Int? = if (y in nutY..1f && x in .33f.. .67f)
        routes.minByOrNull { abs(it.nut.x-x) }?.number else null

    fun minimumHeight(controlHeight: Float): Float = if (type == HeadstockType.THREE_PER_SIDE)
        maxOf(290f, 3 * (controlHeight+4) + 48) else maxOf(440f, 6 * (controlHeight+4) + 64)

    companion object {
        fun forType(type: HeadstockType): HeadstockGeometry = when (type) {
            HeadstockType.THREE_PER_SIDE -> threePerSide
            HeadstockType.SIX_INLINE -> sixInline
            HeadstockType.SIX_INLINE_REVERSE -> reverse
        }
        private fun route(number: Int, x: Float, y: Float, side: TunerSide, nutY: Float): StringRoute {
            val nutX = mapOf(6 to .35f,5 to .41f,4 to .47f,3 to .53f,2 to .59f,1 to .65f).getValue(number)
            return StringRoute(number,RelativePoint(x,y),RelativePoint(x+side.direction*.115f,y),side,
                RelativePoint(nutX,nutY),RelativePoint(nutX,1f))
        }
        private val threePerSide = HeadstockGeometry(HeadstockType.THREE_PER_SIDE,260f,440f,
            outline {
                move(.23f,.115f)
                cubic(.25f,.090f,.30f,.105f,.34f,.088f)
                quad(.38f,.070f,.40f,.075f)
                cubic(.44f,.065f,.46f,.040f,.50f,.040f)
                cubic(.54f,.040f,.56f,.065f,.60f,.075f)
                quad(.62f,.070f,.66f,.088f)
                cubic(.70f,.105f,.75f,.090f,.77f,.115f)
                line(.79f,.51f)
                cubic(.79f,.61f,.71f,.68f,.65f,.73f)
                line(.65f,1f);line(.35f,1f);line(.35f,.73f)
                cubic(.29f,.68f,.21f,.61f,.21f,.51f)
                close()
            }, RelativeBounds(.09f,.025f,.91f,1.01f),HeadstockFinish.DARK_WOOD,
            listOf(
                route(6,.31f,.59f,TunerSide.LEFT,.73f),
                route(5,.29f,.38f,TunerSide.LEFT,.73f),
                route(4,.27f,.17f,TunerSide.LEFT,.73f),
                route(3,.73f,.17f,TunerSide.RIGHT,.73f),
                route(2,.71f,.38f,TunerSide.RIGHT,.73f),
                route(1,.69f,.59f,TunerSide.RIGHT,.73f),
            ),.73f)
        private val sixInline = HeadstockGeometry(HeadstockType.SIX_INLINE,300f,540f,
            outline {
                move(.47f,.075f)
                cubic(.49f,.025f,.61f,.020f,.67f,.040f)
                cubic(.76f,.055f,.87f,.12f,.87f,.19f)
                cubic(.87f,.25f,.79f,.285f,.71f,.30f)
                cubic(.62f,.32f,.58f,.40f,.59f,.48f)
                cubic(.60f,.63f,.68f,.73f,.65f,.83f)
                line(.65f,1f);line(.35f,1f);line(.35f,.83f)
                quad(.28f,.81f,.30f,.74f)
                line(.45f,.13f)
                quad(.455f,.095f,.47f,.075f)
                close()
            },RelativeBounds(.19f,.005f,.90f,1.01f),HeadstockFinish.MAPLE,
            listOf(
                route(6,.37f,.755f,TunerSide.LEFT,.83f),
                route(5,.40f,.625f,TunerSide.LEFT,.83f),
                route(4,.43f,.495f,TunerSide.LEFT,.83f),
                route(3,.46f,.365f,TunerSide.LEFT,.83f),
                route(2,.49f,.235f,TunerSide.LEFT,.83f),
                route(1,.52f,.105f,TunerSide.LEFT,.83f),
            ),.83f)
        private val reverse = HeadstockGeometry(HeadstockType.SIX_INLINE_REVERSE,280f,540f,
            outline {
                move(.31f,.025f)
                line(.57f,.10f);line(.75f,.76f);line(.65f,.83f)
                line(.65f,1f);line(.35f,1f);line(.35f,.83f)
                line(.30f,.68f);line(.33f,.47f);line(.29f,.29f)
                close()
            },RelativeBounds(.26f,.005f,.85f,1.01f),HeadstockFinish.GRAPHITE,
            listOf(
                route(6,.50f,.105f,TunerSide.RIGHT,.83f),
                route(5,.53f,.235f,TunerSide.RIGHT,.83f),
                route(4,.56f,.365f,TunerSide.RIGHT,.83f),
                route(3,.59f,.495f,TunerSide.RIGHT,.83f),
                route(2,.62f,.625f,TunerSide.RIGHT,.83f),
                route(1,.65f,.755f,TunerSide.RIGHT,.83f),
            ),.83f)
    }
}

/** Uniform fit: previews, drawing and pointer coordinates all use the same transformation. */
data class HeadstockViewport(val originX: Float,val originY: Float,val scale: Float,
    val designWidth: Float,val designHeight: Float) {
    fun map(p: RelativePoint) = RelativePoint(originX+p.x*designWidth*scale,originY+p.y*designHeight*scale)
    fun inverse(p: RelativePoint) = RelativePoint((p.x-originX)/(designWidth*scale),(p.y-originY)/(designHeight*scale))
    companion object {
        fun fit(g: HeadstockGeometry,width: Float,height: Float,controlWidth: Float = 0f): HeadstockViewport {
            val left = if (controlWidth>0 && g.routes.any { it.side==TunerSide.LEFT }) controlWidth+6 else 4f
            val right = if (controlWidth>0 && g.routes.any { it.side==TunerSide.RIGHT }) controlWidth+6 else 4f
            val b=g.bounds
            val availableWidth=(width-left-right).coerceAtLeast(1f)
            val spanX=(b.right-b.left)*g.designWidth;val spanY=(b.bottom-b.top)*g.designHeight
            val scale=minOf(availableWidth/spanX,(height-8).coerceAtLeast(1f)/spanY)
            return HeadstockViewport(left+(availableWidth-spanX*scale)/2-b.left*g.designWidth*scale,
                (height-spanY*scale)/2-b.top*g.designHeight*scale,scale,g.designWidth,g.designHeight)
        }
    }
    fun controlCenters(g: HeadstockGeometry,height: Float,controlHeight: Float): Map<Int,Float> = buildMap {
        TunerSide.entries.forEach { side ->
            val rows=g.routes.filter { it.side==side }.sortedBy { it.peg.y }
            if (rows.isEmpty()) return@forEach
            val first=map(rows.first().peg).y;val last=map(rows.last().peg).y
            val spacing=maxOf(controlHeight+4,(last-first)/(rows.size-1).coerceAtLeast(1))
            val span=spacing*(rows.size-1)
            val start=((first+last-span)/2).coerceIn(controlHeight/2+2,
                maxOf(controlHeight/2+2,height-controlHeight/2-2-span))
            rows.forEachIndexed { index,route -> put(route.number,start+index*spacing) }
        }
    }
}
