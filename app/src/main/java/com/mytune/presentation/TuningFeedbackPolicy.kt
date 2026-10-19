package com.mytune.presentation

import com.mytune.domain.audio.SignalQuality
import com.mytune.domain.model.*
import kotlin.math.abs

/** Effects use current accepted readings, never retained results or an arbitrary loud signal. */
fun TunerUiState.hasFreshTargetSignal(): Boolean {
    val target = selectedString ?: return false
    val reading = result ?: return false
    val raw = rawDetection
    return hasPermission && microphoneActive && error == null && tuningsLoaded && !tuningOperationPending &&
        hasTuningGuidance && readingStatus == ReadingStatus.ACTIVE &&
        raw.quality == SignalQuality.PITCHED && raw.frequency?.let { it.isFinite() && it > 0 } == true &&
        raw.confidence in .90..1.0 && raw.rms.isFinite() && raw.rms >= .0025 &&
        reading.confidence in .90..1.0 && reading.frequency.isFinite() && reading.frequency > 0 &&
        reading.cents.isFinite() && reading.note.midi == target.midi &&
        reading.status != TuningStatus.WRONG_NOTE
}

fun TunerUiState.stringVibrationLevel(enabled: Boolean): Float =
    if (enabled && hasFreshTargetSignal()) audioLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f else 0f

/** One success per cycle. Rearming needs a sustained departure, not boundary jitter. */
class TuningFeedbackPolicy {
    private var targetKey: String? = null
    private var armed = true
    private var awaySince: Long? = null
    private var absentSince: Long? = null
    private var lastPulse: Long? = null
    private var wasEnabled = true

    fun accept(state: TunerUiState, enabled: Boolean, nowMs: Long): Boolean {
        val target = state.selectedString
        val key = target?.let { "${state.activeTuning.id}:${it.number}:${it.midi}:${state.tuningMode}" }
        if (key != targetKey) { targetKey = key; armed = true; awaySince = null; absentSince = null }
        val fresh = state.hasFreshTargetSignal()
        if (!enabled) { wasEnabled = false; armed = false; awaySince = null; return false }
        if (!wasEnabled) { wasEnabled = true; armed = false } // Enabling is not a tuning event.
        if (!fresh) {
            awaySince = null
            if (absentSince == null) absentSince = nowMs
            if (nowMs - checkNotNull(absentSince) >= 1800) armed = true
            return false
        }
        if (absentSince?.let { nowMs - it >= 1800 } == true) armed = true
        absentSince = null
        val result = checkNotNull(state.result)
        if (result.status != TuningStatus.IN_TUNE) {
            if (abs(result.cents) >= 12) {
                if (awaySince == null) awaySince = nowMs
                if (nowMs - checkNotNull(awaySince) >= 350) armed = true
            } else awaySince = null
            return false
        }
        awaySince = null
        if (!armed || lastPulse?.let { nowMs - it < 1500 } == true) return false
        armed = false; lastPulse = nowMs
        return true
    }
}
