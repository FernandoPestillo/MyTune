package com.mytune.ui.components.headstock

import com.mytune.domain.model.HeadstockType
import kotlin.math.abs

data class RelativePoint(val x: Float, val y: Float)
data class RelativeBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
enum class TunerSide(val direction: Float) { LEFT(-1f), RIGHT(1f) }
enum class HeadstockFinish { BLACK_GLOSS, MAPLE, GRAPHITE }

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
        private fun route(number: Int, x: Float, y: Float, side: TunerSide, nutY: Float, axle: Float = .17f): StringRoute {
            val nutX = mapOf(6 to .35f,5 to .41f,4 to .47f,3 to .53f,2 to .59f,1 to .65f).getValue(number)
            return StringRoute(number,RelativePoint(x,y),RelativePoint(x+side.direction*axle,y),side,
                RelativePoint(nutX,nutY),RelativePoint(nutX,1f))
        }
        private val threePerSide = HeadstockGeometry(HeadstockType.THREE_PER_SIDE,300f,440f,
            outline {
                move(.18f,.105f)
                cubic(.29f,.122f,.36f,.075f,.42f,.065f)
                quad(.47f,.050f,.50f,.045f)
                quad(.53f,.050f,.58f,.065f)
                cubic(.64f,.075f,.71f,.122f,.82f,.105f)
                cubic(.80f,.25f,.78f,.44f,.81f,.59f)
                cubic(.72f,.625f,.66f,.69f,.65f,.80f)
                line(.65f,1f);line(.35f,1f);line(.35f,.80f)
                cubic(.34f,.69f,.28f,.625f,.19f,.59f)
                cubic(.22f,.44f,.20f,.25f,.18f,.105f)
                close()
            }, RelativeBounds(.035f,.025f,.965f,1.01f),HeadstockFinish.BLACK_GLOSS,
            listOf(
                route(6,.30f,.56f,TunerSide.LEFT,.80f,.20f),
                route(5,.30f,.385f,TunerSide.LEFT,.80f,.20f),
                route(4,.30f,.21f,TunerSide.LEFT,.80f,.20f),
                route(3,.70f,.21f,TunerSide.RIGHT,.80f,.20f),
                route(2,.70f,.385f,TunerSide.RIGHT,.80f,.20f),
                route(1,.70f,.56f,TunerSide.RIGHT,.80f,.20f),
            ),.80f)
        private val sixInline = HeadstockGeometry(HeadstockType.SIX_INLINE,380f,620f,
            outline {
                move(.53f,.105f)
                cubic(.55f,.035f,.64f,.008f,.73f,.022f)
                cubic(.85f,.035f,.94f,.105f,.92f,.175f)
                cubic(.91f,.235f,.86f,.257f,.79f,.280f)
                cubic(.72f,.305f,.705f,.355f,.72f,.415f)
                cubic(.73f,.50f,.80f,.61f,.805f,.655f)
                cubic(.81f,.68f,.76f,.68f,.71f,.710f)
                cubic(.675f,.737f,.654f,.775f,.65f,.80f)
                line(.65f,1f);line(.35f,1f);line(.35f,.80f)
                cubic(.33f,.775f,.265f,.75f,.22f,.71f)
                cubic(.18f,.684f,.18f,.66f,.20f,.617f)
                cubic(.29f,.475f,.40f,.275f,.53f,.105f)
                close()
            },RelativeBounds(.085f,.005f,.95f,1.01f),HeadstockFinish.MAPLE,
            listOf(
                route(6,.31f,.69f,TunerSide.LEFT,.80f),
                route(5,.37f,.58f,TunerSide.LEFT,.80f),
                route(4,.43f,.47f,TunerSide.LEFT,.80f),
                route(3,.49f,.36f,TunerSide.LEFT,.80f),
                route(2,.55f,.25f,TunerSide.LEFT,.80f),
                route(1,.61f,.14f,TunerSide.LEFT,.80f),
            ),.80f)
        private val reverse = HeadstockGeometry(HeadstockType.SIX_INLINE_REVERSE,380f,660f,
            outline {
                move(.25f,.02f)
                line(.51f,.125f);line(.82f,.685f)
                line(.735f,.727f)
                quad(.67f,.757f,.65f,.83f)
                line(.65f,1f);line(.35f,1f);line(.35f,.83f)
                cubic(.34f,.737f,.26f,.67f,.14f,.615f)
                quad(.12f,.605f,.145f,.58f)
                cubic(.36f,.405f,.37f,.26f,.25f,.02f)
                close()
            },RelativeBounds(.11f,.005f,.985f,1.01f),HeadstockFinish.GRAPHITE,
            listOf(
                route(6,.43f,.215f,TunerSide.RIGHT,.83f),
                route(5,.485f,.31f,TunerSide.RIGHT,.83f),
                route(4,.54f,.405f,TunerSide.RIGHT,.83f),
                route(3,.595f,.50f,TunerSide.RIGHT,.83f),
                route(2,.65f,.595f,TunerSide.RIGHT,.83f),
                route(1,.705f,.69f,TunerSide.RIGHT,.83f),
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
