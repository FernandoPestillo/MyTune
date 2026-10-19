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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mytune.domain.model.GuitarStringNote
import com.mytune.ui.theme.*

@Composable
fun GuitarString(string: GuitarStringNote, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    selectionAlpha: Float = 1f) {
    val background by animateColorAsState(if (selected) Orange.copy(alpha = .12f * selectionAlpha) else Panel, tween(140), label = "stringBackground")
    val border by animateColorAsState(if (selected) Orange.copy(alpha = selectionAlpha) else Muted.copy(alpha = .16f), tween(140), label = "stringBorder")
    val text by animateColorAsState(if (selected) Orange.copy(alpha = selectionAlpha) else Foreground, tween(140), label = "stringText")
    Column(modifier.width(66.dp).height(58.dp)
        .background(background, RoundedCornerShape(14.dp))
        .border(1.dp, border, RoundedCornerShape(14.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = "${string.number}ª corda, ${string.label}" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(string.label, color = text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text("${string.number}ª CORDA", color = if (selected) Orange.copy(alpha = .8f) else Muted, fontSize = 8.sp, letterSpacing = .5.sp)
    }
}
