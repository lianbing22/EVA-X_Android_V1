package com.evax.mobile.platform.voice

import com.evax.mobile.domain.SpeechInputFailure

interface SpeechInputController {
    fun start(
        onResult: (String) -> Unit,
        onFailure: (SpeechInputFailure) -> Unit,
    )

    fun cancel()

    fun destroy()
}
