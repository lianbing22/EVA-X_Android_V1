package com.evax.mobile.platform.voice

import com.evax.mobile.domain.SpeechInputFailure

interface SpeechInputController {
    fun start(
        onResult: (String) -> Unit,
        onFailure: (SpeechInputFailure) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onRmsChanged: (Float) -> Unit = {},
    )

    fun cancel()

    fun destroy()
}
