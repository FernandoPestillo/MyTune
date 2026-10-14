package com.mytune.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/** Measures every supported message at the current width/font scale, including wrapping. */
@Composable
fun StableText(text: String, variants: List<String>, style: TextStyle, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, softWrap: Boolean = true) {
    Box(modifier, contentAlignment = Alignment.Center) {
        variants.forEach { variant ->
            Text(variant, Modifier.alpha(0f).clearAndSetSemantics { }, style = style,
                textAlign = TextAlign.Center, maxLines = maxLines, softWrap = softWrap)
        }
        Text(text, style = style, textAlign = TextAlign.Center, maxLines = maxLines, softWrap = softWrap)
    }
}
