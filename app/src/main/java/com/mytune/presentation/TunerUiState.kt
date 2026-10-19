package com.mytune.presentation

import com.mytune.domain.audio.SignalQuality
import com.mytune.domain.audio.PitchDetection
import com.mytune.domain.model.*

data class TunerUiState(
    val selectedString: GuitarStringNote? = StandardTuning.strings.first(),
    val tuningMode: TuningMode = TuningMode.MANUAL,
    val automaticSelectionStatus: AutomaticSelectionStatus = AutomaticSelectionStatus.WAITING,
    val stringClassification: StringDetectionResult? = null,
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
) {
    /** A retained automatic reading is not fresh confirmation of its target. */
    val hasTuningGuidance: Boolean get() = readingStatus != ReadingStatus.STALE &&
        (tuningMode == TuningMode.MANUAL || automaticSelectionStatus == AutomaticSelectionStatus.TRACKING)
}
