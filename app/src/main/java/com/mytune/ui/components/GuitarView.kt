package com.mytune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.mytune.domain.model.*
import com.mytune.ui.theme.*
import kotlin.math.*

@Composable
fun GuitarView(selected: GuitarStringNote?, onSelect: (GuitarStringNote) -> Unit, modifier: Modifier = Modifier,
    selectionAlpha: Float = 1f) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val emphasis = StandardTuning.strings.map { string ->
        animateFloatAsState(if (selected == string) selectionAlpha else 0f, tween(140), label = "string_${string.number}").value
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { point ->
                // String lanes follow the same normalized coordinates as the drawing.
                if (point.x in size.width * .33f..size.width * .67f) {
                    val index = ((point.x / size.width - .375f) / .05f).roundToInt().coerceIn(0, 5)
                    currentOnSelect(StandardTuning.strings[index])
                }
            }
        }) {
            val w = size.width
            val h = size.height
            fun p(x: Float, y: Float) = Offset(w * x, h * y)
            drawCircle(Brush.radialGradient(listOf(Orange.copy(alpha = .075f), Color.Transparent),
                p(.5f, .42f), w * .45f), w * .45f, p(.5f, .42f))
            val head = Path().apply {
                moveTo(w * .35f, h * .055f)
                cubicTo(w * .39f, h * -.015f, w * .61f, h * -.015f, w * .65f, h * .055f)
                lineTo(w * .665f, h * .43f)
                quadraticTo(w * .665f, h * .52f, w * .63f, h * .59f)
                lineTo(w * .63f, h); lineTo(w * .37f, h)
                lineTo(w * .37f, h * .59f)
                quadraticTo(w * .335f, h * .52f, w * .335f, h * .43f)
                close()
            }
            drawPath(head, Brush.horizontalGradient(listOf(Color(0xFF302A25), Color(0xFF514335), Color(0xFF302A25)), w * .33f, w * .67f))
            drawPath(head, Color(0xFF6A5541), style = Stroke(1.dp.toPx()))
            // Restrained wood grain, contained inside the headstock and neck.
            for (i in 0..16) {
                val x = .38f + i * .015f
                drawLine(Color(0xFFDDAC74).copy(alpha = .035f), p(x, .05f), p(x, 1f), 1.dp.toPx())
            }
            for (row in 0..2) {
                val y = .16f + row * .205f
                for (side in listOf(-1, 1)) {
                    val outer = if (side == -1) .295f else .705f
                    drawLine(Color(0xFF808186), p(outer, y), p(if (side == -1) .38f else .62f, y), 4.dp.toPx())
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF777A7E), Color(0xFF343639))),
                        p(outer - .028f, y - .045f), Size(w * .056f, h * .09f), CornerRadius(6.dp.toPx()))
                    drawCircle(Color(0xFF252629), 9.dp.toPx(), p(if (side == -1) .38f else .62f, y))
                    drawCircle(Color(0xFFA0A1A2), 5.dp.toPx(), p(if (side == -1) .38f else .62f, y))
                }
            }
            // Fretboard, nut, frets and inlays make the instrument recognizable at small sizes.
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF211D1A), Color(0xFF352D26), Color(0xFF211D1A))),
                p(.37f, .62f), Size(w * .26f, h * .38f))
            for (y in listOf(.74f, .86f, .97f))
                drawLine(Color(0xFF76716A), p(.37f, y), p(.63f, y), 2.dp.toPx())
            drawCircle(Color(0xFF8B8276), 3.dp.toPx(), p(.5f, .805f))
            drawLine(Color(0xFFB7AEA0), p(.366f, .625f), p(.634f, .625f), 5.dp.toPx())
            StandardTuning.strings.forEachIndexed { index, string ->
                val x = .375f + index * .05f
                val pegY = .16f + (if (index < 3) index else 5 - index) * .205f
                val pegX = if (index < 3) .38f else .62f
                val strength = emphasis[index]
                val color = lerp(Color(0xFFBEB7AA).copy(alpha = .5f), Orange, strength)
                val thickness = (2.1f - index * .22f).dp.toPx()
                if (strength > 0f) drawLine(Orange.copy(alpha = .10f * strength), p(x, .625f), p(x, 1f), 10.dp.toPx())
                drawLine(color, p(pegX, pegY), p(x, .625f), thickness, StrokeCap.Round)
                drawLine(color, p(x, .625f), p(x, 1f), thickness, StrokeCap.Round)
            }
        }
        listOf(0, 1, 2).forEach { row ->
            val y = maxHeight * (.16f + row * .205f) - 29.dp
            val left = StandardTuning.strings[row]
            val right = StandardTuning.strings[5 - row]
            GuitarString(left, selected == left, { onSelect(left) }, Modifier.align(Alignment.TopStart).offset(y = y).testTag("guitarString_${left.number}"), selectionAlpha)
            GuitarString(right, selected == right, { onSelect(right) }, Modifier.align(Alignment.TopEnd).offset(y = y).testTag("guitarString_${right.number}"), selectionAlpha)
        }
        Text("MYTUNE", color = Color(0xFFBFA88B), fontSize = 8.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}
