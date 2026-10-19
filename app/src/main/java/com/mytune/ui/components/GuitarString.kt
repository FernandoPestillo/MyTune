package com.mytune.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.mytune.domain.model.GuitarStringNote
import com.mytune.ui.theme.*

fun guitarStringControlHeight(fontScale: Float): Dp = maxOf(58.dp, 36.dp * fontScale + 12.dp)

@Composable
fun GuitarString(string: GuitarStringNote, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    selectionAlpha: Float = 1f, compact: Boolean = false, animationsEnabled: Boolean = true) {
    val fontScale = LocalDensity.current.fontScale
    val duration = if (animationsEnabled && LocalMotionEnabled.current) 140 else 0
    val background by animateColorAsState(if (selected) Orange.copy(alpha = .08f * selectionAlpha) else Panel, tween(duration), label = "stringBackground")
    val border by animateColorAsState(if (selected) Orange.copy(alpha = selectionAlpha) else Muted.copy(alpha = .16f), tween(duration), label = "stringBorder")
    val text by animateColorAsState(if (selected) Orange else Foreground, tween(duration), label = "stringText")
    Column(modifier.width(maxOf(if (compact) 82.dp else 66.dp, 50.dp * fontScale + 8.dp))
        .height(guitarStringControlHeight(fontScale))
        .background(background, RoundedCornerShape(14.dp))
        .border(1.dp, border, RoundedCornerShape(14.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .semantics {
            contentDescription = "${string.number}ª corda, ${string.label}"
            stateDescription = if (selected) "Selecionada" else "Não selecionada"
        }.padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(string.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = text, fontSize = 20.sp, lineHeight = 24.sp, maxLines = 1, softWrap = false, fontWeight = FontWeight.Medium)
        Text(if (selected) "${string.number}ª ✓" else "${string.number}ª CORDA", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = if (selected) Orange else Muted, fontSize = 8.sp, lineHeight = 12.sp, maxLines = 1, softWrap = false, letterSpacing = .5.sp)
    }
}
