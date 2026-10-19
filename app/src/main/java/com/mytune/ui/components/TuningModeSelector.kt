package com.mytune.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.mytune.domain.model.TuningMode
import com.mytune.ui.theme.*

@Composable
fun TuningModeSelector(mode: TuningMode, onModeChange: (TuningMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("tuningModeSelector")) {
        TuningMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, TuningMode.entries.size),
                modifier = Modifier.testTag("mode_${option.name}"),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = Orange.copy(alpha = .12f),
                    activeContentColor = Orange, activeBorderColor = Orange,
                    inactiveContainerColor = Panel, inactiveContentColor = Muted,
                    inactiveBorderColor = Muted.copy(alpha = .3f)),
                icon = {},
            ) {
                Text(if (option == TuningMode.MANUAL) "Manual" else "Automático", fontSize = 13.sp,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}
