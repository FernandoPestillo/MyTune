package com.mytune.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.mytune.domain.model.*
import com.mytune.ui.components.headstock.*
import kotlin.math.PI
import kotlin.math.sin

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
    val fontScale = LocalDensity.current.fontScale
    val inline = headstock != HeadstockType.THREE_PER_SIDE
    val buttonHeight = guitarStringControlHeight(fontScale)
    val buttonWidth = guitarStringControlWidth(fontScale, inline)
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewport = HeadstockViewport.fit(geometry,maxWidth.value,maxHeight.value,buttonWidth.value)
        val centers = viewport.controlCenters(geometry,maxHeight.value,buttonHeight.value)
        Box(Modifier.fillMaxSize().testTag("headstockCanvas").pointerInput(headstock, viewport) {
            detectTapGestures { point ->
                val relative = viewport.inverse(RelativePoint(point.x/density,point.y/density))
                val peg = geometry.pegAt(relative.x,relative.y,
                    24f/(viewport.designWidth*viewport.scale),24f/(viewport.designHeight*viewport.scale))
                val number = peg ?: geometry.neckStringAt(relative.x,relative.y)
                currentStrings.firstOrNull { it.number==number }?.let(currentOnSelect)
            }
        }) {
            // The illustration fades, while one set of six accessible controls remains mounted.
            Crossfade(headstock, animationSpec=tween(if(motion) 220 else 0),label="headstock") { model ->
                HeadstockDrawing(model,emphasis.mapValues { it.value },selected?.number,wave,
                    buttonWidth.value,buttonHeight.value)
            }
        }
        geometry.routes.forEach { route ->
            val string=ordered.first { it.number==route.number }
            GuitarString(string,selected?.number==string.number,{onSelect(string)},
                Modifier.align(if(route.side==TunerSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset(y=(centers.getValue(route.number)-buttonHeight.value/2).dp)
                    .testTag("guitarString_${string.number}"),selectionAlpha,
                compact=inline,animationsEnabled=motion)
        }
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

/** Same proportional illustration as the tuner, without the interactive sidebar controls. */
@Composable
fun HeadstockPreview(type: HeadstockType, modifier: Modifier = Modifier) {
    val zero=remember { mutableFloatStateOf(0f) }
    Box(modifier) { HeadstockDrawing(type,emptyMap(),null,zero) }
}
