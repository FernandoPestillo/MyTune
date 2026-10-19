package com.mytune.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.mytune.domain.model.*
import com.mytune.ui.components.headstock.*
import com.mytune.ui.theme.*
import kotlin.math.*

@Composable
fun GuitarView(selected: GuitarStringNote?, onSelect: (GuitarStringNote) -> Unit, modifier: Modifier = Modifier,
    selectionAlpha: Float = 1f, strings: List<GuitarStringNote> = StandardTuning.strings,
    headstock: HeadstockType = HeadstockType.THREE_PER_SIDE, vibrationLevel: Float = 0f,
    animationsEnabled: Boolean = true) {
    val ordered = strings.sortedByDescending { it.number }
    val currentStrings by rememberUpdatedState(ordered)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val geometry = remember(headstock) { HeadstockGeometry.forType(headstock) }
    val motion = LocalMotionEnabled.current && animationsEnabled
    val emphasis = ordered.associate { string -> string.number to animateFloatAsState(
        if (selected?.number == string.number) selectionAlpha else 0f,
        tween(if (motion) 140 else 0), label = "string_${string.number}") }
    val wave = rememberStringWave(vibrationLevel, motion, selected?.number)
    // The wood stays dark in both themes; retain a bright string accent on this material.
    val orange = DarkColors.primary
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxSize().pointerInput(headstock) {
            detectTapGestures { point ->
                val x = point.x / size.width; val y = point.y / size.height
                val peg = geometry.pegAt(x, y, 24.dp.toPx() / size.width, 24.dp.toPx() / size.height)
                val number = peg ?: if (x in .33f.. .67f && y >= geometry.nutY)
                    6 - ((x - .375f) / .05f).roundToInt().coerceIn(0, 5) else null
                currentStrings.firstOrNull { it.number == number }?.let(currentOnSelect)
            }
        }) {
            // Only the drawing crossfades. One set of semantic/interactive controls stays mounted.
            Crossfade(headstock, animationSpec = tween(if (motion) 220 else 0), label = "headstock") { model ->
                HeadstockDrawing(model, emphasis.mapValues { it.value }, selected?.number, wave, orange)
            }
        }
        val fontScale = LocalDensity.current.fontScale
        val inline = headstock == HeadstockType.SIX_INLINE
        val buttonHeight = guitarStringControlHeight(fontScale)
        geometry.routes.forEach { route ->
            val string = ordered.first { it.number == route.number }
            GuitarString(string, selected?.number == string.number, { onSelect(string) },
                Modifier.align(if (route.button.x == 0f) Alignment.TopStart else Alignment.TopEnd)
                    .offset(y = (maxHeight * route.button.y - buttonHeight / 2).coerceIn(0.dp, maxHeight - buttonHeight))
                    .testTag("guitarString_${string.number}"), selectionAlpha,
                compact = inline, animationsEnabled = motion)
        }
        Text("MYTUNE", color = InstrumentColors.logo, fontSize = 8.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}

@Composable
internal fun rememberStringWave(level: Float, enabled: Boolean, stringNumber: Int?): State<Float> {
    val active = enabled && level > 0f && stringNumber != null
    val amplitude = animateFloatAsState(if (active) .25f + level.coerceIn(0f, 1f) * 1.25f else 0f,
        tween(if (enabled) 220 else 0), label = "vibrationAmplitude")
    // The infinite transition exists only during a valid signal and its short fade-out.
    // Phase is read by the Canvas, so it does not recompose the screen every frame.
    val running = enabled && (active || amplitude.value > .01f)
    if (!running) return remember { mutableFloatStateOf(0f) }
    val phase = rememberInfiniteTransition(label = "stringVibration").animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(180, easing = LinearEasing)), label = "vibrationPhase")
    return remember(phase, amplitude) { derivedStateOf { sin(phase.value) * amplitude.value } }
}

@Composable
private fun HeadstockDrawing(type: HeadstockType, emphasis: Map<Int, State<Float>>, selected: Int?,
    wave: State<Float>, accent: Color) {
    val geometry = remember(type) { HeadstockGeometry.forType(type) }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        val inline = type == HeadstockType.SIX_INLINE
        val nutY = geometry.nutY
        val head = Path().apply {
            if (!inline) {
                moveTo(w * .35f, h * .055f)
                cubicTo(w * .39f, h * .005f, w * .61f, h * .005f, w * .65f, h * .055f)
                lineTo(w * .665f, h * .43f)
                quadraticTo(w * .665f, h * .52f, w * .63f, h * .59f)
            } else {
                moveTo(w * .34f, h * .04f)
                cubicTo(w * .39f, h * .005f, w * .63f, h * .015f, w * .67f, h * .09f)
                cubicTo(w * .74f, h * .18f, w * .54f, h * .23f, w * .56f, h * .36f)
                cubicTo(w * .57f, h * .58f, w * .65f, h * .73f, w * .63f, h * .86f)
            }
            lineTo(w * .63f, h); lineTo(w * .37f, h)
            lineTo(w * .37f, h * (if (inline) .86f else .59f))
            quadraticTo(w * .335f, h * (if (inline) .80f else .52f), w * .335f, h * .43f)
            close()
        }
        drawPath(head, Brush.horizontalGradient(listOf(InstrumentColors.woodEdge, InstrumentColors.wood, InstrumentColors.woodEdge), w * .33f, w * .67f))
        drawPath(head, InstrumentColors.woodOutline, style = Stroke(1.dp.toPx()))
        drawRect(InstrumentColors.fretboard, p(.37f, nutY), Size(w * .26f, h * (1 - nutY)))
        for (fraction in listOf(.3f, .62f, .92f)) {
            val y = nutY + (1 - nutY) * fraction
            drawLine(InstrumentColors.fret, p(.37f, y), p(.63f, y), 2.dp.toPx())
        }
        drawLine(InstrumentColors.nut, p(.366f, nutY), p(.634f, nutY), 5.dp.toPx())
        geometry.routes.forEachIndexed { index, route ->
            val peg = p(route.peg.x, route.peg.y)
            val outer = route.peg.x + if (inline || route.number >= 4) -.085f else .085f
            val y = route.peg.y
            drawLine(InstrumentColors.metal, p(outer, y), peg, 4.dp.toPx())
            drawRoundRect(Brush.verticalGradient(listOf(InstrumentColors.metal, InstrumentColors.metalDark)),
                p(outer - .028f, y - .035f), Size(w * .056f, h * .07f), CornerRadius(5.dp.toPx()))
            drawCircle(InstrumentColors.peg, 8.dp.toPx(), peg)
            drawCircle(InstrumentColors.metal, 4.dp.toPx(), peg)
            val strength = emphasis[route.number]?.value ?: 0f
            val color = lerp(InstrumentColors.string.copy(alpha = .65f), accent, strength)
            val thickness = (2.1f - index * .22f).dp.toPx()
            val nut = p(route.nut.x, route.nut.y); val end = p(route.end.x, route.end.y)
            if (strength > 0f) drawLine(accent.copy(alpha = .10f * strength), nut, end, 10.dp.toPx())
            val displacement = if (selected == route.number) wave.value.dp.toPx() else 0f
            val path = Path().apply {
                moveTo(peg.x, peg.y)
                quadraticTo((peg.x + nut.x) / 2 + displacement, (peg.y + nut.y) / 2, nut.x, nut.y)
                quadraticTo(nut.x + displacement, (nut.y + end.y) / 2, end.x, end.y)
            }
            drawPath(path, color, style = Stroke(thickness, cap = StrokeCap.Round))
        }
    }
}

/** Settings previews reuse the production geometry without adding selectable string controls. */
@Composable
fun HeadstockPreview(type: HeadstockType, modifier: Modifier = Modifier) {
    val zero = remember { mutableFloatStateOf(0f) }
    Box(modifier) { HeadstockDrawing(type, emptyMap(), null, zero, Orange) }
}
