package com.mytune.data.audio

/** Buffers are borrowed and valid only during the synchronous callback. */
interface AudioCaptureSource {
    suspend fun capture(onStarted: (Int) -> Unit, onSamples: (FloatArray, Int) -> Unit)
}

class AudioCaptureException(message: String) : Exception(message)
