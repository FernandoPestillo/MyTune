package com.mytune.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mytune.domain.model.GuitarStringNote
import com.mytune.ui.theme.*

@Composable
fun GuitarString(string: GuitarStringNote, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(66.dp).height(58.dp)
        .background(if (selected) Orange.copy(alpha = .12f) else Panel, RoundedCornerShape(14.dp))
        .border(1.dp, if (selected) Orange else Muted.copy(alpha = .16f), RoundedCornerShape(14.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = "${string.number}ª corda, ${string.label}" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(string.label, color = if (selected) Orange else Foreground, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text("${string.number}ª CORDA", color = if (selected) Orange.copy(alpha = .8f) else Muted, fontSize = 8.sp, letterSpacing = .5.sp)
    }
}
