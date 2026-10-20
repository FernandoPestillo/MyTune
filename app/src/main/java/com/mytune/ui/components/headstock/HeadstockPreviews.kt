package com.mytune.ui.components.headstock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.mytune.domain.model.HeadstockType
import com.mytune.ui.theme.MyTuneTheme

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
