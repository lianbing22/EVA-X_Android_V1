package com.evax.mobile.platform.voice

import com.evax.mobile.presentation.VoicePlaybackState
import kotlinx.coroutines.flow.StateFlow

interface SpeechOutputController {
    val state: StateFlow<VoicePlaybackState>

    fun speak(text: String)

    fun speakChunk(text: String, flush: Boolean = false) {
        speak(text)
    }

    fun setSpeechRate(rate: Float) {}

    fun cycleSpeechRate(): Float = state.value.speechRate

    fun stop()

    fun shutdown()
}
