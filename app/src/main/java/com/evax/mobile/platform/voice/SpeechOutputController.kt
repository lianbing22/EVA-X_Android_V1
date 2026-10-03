package com.evax.mobile.platform.voice

import com.evax.mobile.presentation.VoicePlaybackState
import kotlinx.coroutines.flow.StateFlow

interface SpeechOutputController {
    val state: StateFlow<VoicePlaybackState>

    fun speak(text: String)

    fun stop()

    fun shutdown()
}
