package com.mytune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytune.domain.model.*
import com.mytune.ui.theme.*
import java.util.Locale

@Composable
fun TuningIndicator(result: TuningResult?, isCurrent: Boolean = true) {
    val mutedColor = Muted
    val greenColor = Green
    val tickColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
    val cents by animateFloatAsState((result?.cents ?: 0.0).coerceIn(-50.0, 50.0).toFloat(),
        tween(110), label = "cents")
    val pointerAlpha by animateFloatAsState(if (result == null) 0f else 1f, tween(180), label = "pointerAlpha")
    val color by animateColorAsState(if (result == null || !isCurrent) Muted else if (result.status == TuningStatus.IN_TUNE) Green else Orange,
        tween(180), label = "tuningColor")
    val description = result?.let { String.format(Locale.forLanguageTag("pt-BR"), "%+.1f cents", it.cents) }
        ?: "Sem detecção válida"
    Column(Modifier.testTag("tuningIndicator").semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(65.dp)) {
            val margin = 5.dp.toPx()
            val width = size.width - 2 * margin
            val center = size.width / 2
            val centerColor = if (isCurrent && result?.status == TuningStatus.IN_TUNE) greenColor else mutedColor
            drawRoundRect(centerColor.copy(alpha = 0.08f), Offset(center - width * 0.05f, 8.dp.toPx()),
                Size(width * 0.1f, 40.dp.toPx()), CornerRadius(6.dp.toPx()))
            for (tick in -10..10) {
                val x = margin + (tick + 10) / 20f * width
                val major = tick % 5 == 0
                drawLine(if (tick == 0) centerColor else tickColor,
                    Offset(x, 22.dp.toPx()), Offset(x, (if (major) 43 else 34).dp.toPx()),
                    if (tick == 0) 2.dp.toPx() else 1.dp.toPx())
            }
            // Expiration gently returns to center while fading; no idle pointer remains.
            if (pointerAlpha > 0f) {
                val x = center + cents / 100 * width
                drawCircle(color.copy(alpha = 0.12f * pointerAlpha), 15.dp.toPx(), Offset(x, 10.dp.toPx()))
                val pointer = Path().apply {
                    moveTo(x - 5.dp.toPx(), 3.dp.toPx()); lineTo(x + 5.dp.toPx(), 3.dp.toPx())
                    lineTo(x, 13.dp.toPx()); close()
                }
                drawPath(pointer, color.copy(alpha = pointerAlpha))
                drawLine(color.copy(alpha = pointerAlpha), Offset(x, 19.dp.toPx()), Offset(x, 47.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("−50", Modifier.weight(1f), color = Muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
            Text(if (result == null) "CENTS" else description, Modifier.weight(4f), textAlign = TextAlign.Center,
                style = TextStyle(fontFeatureSettings = "tnum"), color = if (result == null) Muted else color,
                fontSize = 10.sp, maxLines = 1, softWrap = false)
            Text("+50", Modifier.weight(1f), textAlign = TextAlign.End, color = Muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
        }
    }
}
