package com.evax.mobile.platform.voice

import android.speech.SpeechRecognizer
import com.evax.mobile.domain.SpeechInputFailure

object SpeechErrorMapper {
    fun map(errorCode: Int): SpeechInputFailure = when (errorCode) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechInputFailure.NO_MATCH

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechInputFailure.NETWORK

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechInputFailure.PERMISSION_DENIED

        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechInputFailure.SERVICE_UNAVAILABLE

        else -> SpeechInputFailure.UNKNOWN
    }
}
