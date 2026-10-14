package com.mytune.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.mytune.ui.theme.*

@Composable
fun AudioLevelIndicator(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(if (active) level else 0f, label = "audioLevel")
    Row(modifier.semantics { contentDescription = if (active) "Microfone ativo" else "Microfone inativo" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(6.dp).background(if (active) Green else Muted, CircleShape))
        Text(if (active) "Microfone ativo" else "Microfone inativo", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(16) { index ->
                Box(Modifier.width(3.dp).height((8 + (index % 4) * 3).dp)
                    .background(if (active && animated > index / 16f) Orange else Muted.copy(alpha = .18f), RoundedCornerShape(2.dp)))
            }
        }
    }
}
