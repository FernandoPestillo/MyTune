package com.mytune.presentation

import com.mytune.domain.audio.SignalQuality
import com.mytune.domain.audio.PitchDetection
import com.mytune.domain.model.*

data class TunerUiState(
    val selectedString: GuitarStringNote = StandardTuning.strings.first(),
    val result: TuningResult? = null,
    val readingStatus: ReadingStatus = ReadingStatus.ABSENT,
    val rawDetection: PitchDetection = PitchDetection(),
    val lastValidDetectionMs: Long? = null,
    val signalQuality: SignalQuality = SignalQuality.SILENCE,
    val audioLevel: Float = 0f,
    val hasPermission: Boolean = false,
    val microphoneActive: Boolean = false,
    val sampleRate: Int? = null,
    val error: String? = null,
)
