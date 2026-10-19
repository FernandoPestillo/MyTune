package com.mytune.ui.components.headstock

import com.mytune.domain.model.HeadstockType
import kotlin.math.abs

data class RelativePoint(val x: Float, val y: Float)
data class StringRoute(val number: Int, val peg: RelativePoint, val button: RelativePoint,
    val nut: RelativePoint, val end: RelativePoint)

/** One geometry feeds drawing, peg hit testing and accessible string controls. Physical IDs only. */
data class HeadstockGeometry(val type: HeadstockType, val routes: List<StringRoute>, val nutY: Float) {
    fun pegAt(x: Float, y: Float, radiusX: Float, radiusY: Float): Int? = routes
        .filter { abs(it.peg.x - x) <= radiusX && abs(it.peg.y - y) <= radiusY }
        .minByOrNull { abs(it.peg.x - x) / radiusX + abs(it.peg.y - y) / radiusY }?.number

    companion object {
        fun forType(type: HeadstockType): HeadstockGeometry {
            val nutY = if (type == HeadstockType.THREE_PER_SIDE) .625f else .86f
            return HeadstockGeometry(type, (0..5).map { index ->
                val left = index < 3
                val y = if (type == HeadstockType.THREE_PER_SIDE)
                    .16f + (if (left) index else 5 - index) * .205f else .80f - index * .14f
                val pegX = if (type == HeadstockType.SIX_INLINE) .39f else if (left) .38f else .62f
                val buttonX = if (type == HeadstockType.SIX_INLINE || left) 0f else 1f
                val nutX = .375f + index * .05f
                StringRoute(6 - index, RelativePoint(pegX, y), RelativePoint(buttonX, y),
                    RelativePoint(nutX, nutY), RelativePoint(nutX, 1f))
            }, nutY)
        }
    }
}
