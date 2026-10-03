package com.evax.mobile.platform.voice

import android.speech.SpeechRecognizer
import com.evax.mobile.domain.SpeechInputFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechErrorMapperTest {
    @Test
    fun mapsNoMatchToRetryableFailure() {
        assertEquals(
            SpeechInputFailure.NO_MATCH,
            SpeechErrorMapper.map(SpeechRecognizer.ERROR_NO_MATCH),
        )
        assertEquals(
            SpeechInputFailure.NO_MATCH,
            SpeechErrorMapper.map(SpeechRecognizer.ERROR_SPEECH_TIMEOUT),
        )
    }

    @Test
    fun mapsUnavailableOrNetworkFailure() {
        assertEquals(
            SpeechInputFailure.SERVICE_UNAVAILABLE,
            SpeechErrorMapper.map(SpeechRecognizer.ERROR_SERVER),
        )
        assertEquals(
            SpeechInputFailure.NETWORK,
            SpeechErrorMapper.map(SpeechRecognizer.ERROR_NETWORK),
        )
    }
}
