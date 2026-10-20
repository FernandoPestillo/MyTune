package com.mytune.ui.components.headstock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.mytune.domain.model.HeadstockType
import com.mytune.ui.theme.MyTuneTheme
import com.mytune.ui.components.HeadstockPreview

class HeadstockPreviewModels : PreviewParameterProvider<HeadstockType> {
    override val values = HeadstockType.entries.asSequence()
}

@Preview(name = "Headstocks", widthDp = 200, heightDp = 340, showBackground = true)
@Composable
private fun HeadstockModelPreview(@PreviewParameter(HeadstockPreviewModels::class) model: HeadstockType) {
    val zero = remember { mutableFloatStateOf(0f) }
    MyTuneTheme {
        Surface(Modifier.fillMaxSize()) { HeadstockDrawing(model, emptyMap(), null, zero) }
    }
}

/** Review surface only: all three production renderers, on the reference's neutral background. */
@Composable
internal fun HeadstockComparison(modifier: Modifier = Modifier) {
    Row(modifier.background(Color.White).padding(16.dp).testTag("headstockComparison"),
        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        HeadstockType.entries.forEach { type ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(type.title, color = Color(0xFF292C30), fontSize = 12.sp)
                HeadstockPreview(type, Modifier.fillMaxWidth().weight(1f)
                    .testTag("preview_${type.name}"))
            }
        }
    }
}

@Preview(name = "Comparação dos três headstocks", widthDp = 780, heightDp = 440, showBackground = true)
@Composable
private fun HeadstocksSideBySidePreview() {
    MyTuneTheme { HeadstockComparison(Modifier.fillMaxSize()) }
}
