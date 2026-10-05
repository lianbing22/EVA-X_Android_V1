package com.evax.mobile.platform.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.evax.mobile.presentation.VoicePlaybackState
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidTextToSpeechController(context: Context) : SpeechOutputController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(VoicePlaybackState(speechRate = 1.25f))
    private val utteranceSequence = AtomicLong(0)
    private val playback = VoicePlaybackTracker()

    @Volatile
    private var textToSpeech: TextToSpeech? = null

    @Volatile
    private var currentSpeechRate: Float = 1.25f

    @Volatile
    private var isShutdown = false

    override val state = mutableState.asStateFlow()

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            onMain { initialize(status) }
        }
    }

    override fun speak(text: String) {
        speakChunk(text, flush = true)
    }

    override fun speakChunk(text: String, flush: Boolean) {
        onMain {
            val engine = textToSpeech ?: return@onMain
            val cleaned = text.trim()
            if (isShutdown || !mutableState.value.isReady || cleaned.isBlank()) return@onMain

            runCatching {
                engine.setSpeechRate(currentSpeechRate)
                engine.setPitch(1.03f)
            }

            val utteranceId = "eva-${utteranceSequence.incrementAndGet()}"
            mutableState.value = playback.enqueue(utteranceId, flush)

            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = runCatching { engine.speak(cleaned, queueMode, null, utteranceId) }.getOrDefault(TextToSpeech.ERROR)
            if (result == TextToSpeech.ERROR) {
                mutableState.value = playback.onError(utteranceId, TextToSpeech.ERROR)
            }
        }
    }

    override fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.8f, 2.0f)
        currentSpeechRate = clamped
        onMain {
            runCatching { textToSpeech?.setSpeechRate(clamped) }
            mutableState.value = playback.setSpeechRate(clamped)
        }
    }

    override fun cycleSpeechRate(): Float {
        val next = when {
            currentSpeechRate < 1.15f -> 1.25f
            currentSpeechRate < 1.40f -> 1.50f
            else -> 1.00f
        }
        setSpeechRate(next)
        return next
    }

    override fun stop() {
        onMain {
            mutableState.value = playback.stop()
            runCatching { textToSpeech?.stop() }
        }
    }

    override fun shutdown() {
        onMain {
            if (isShutdown) return@onMain
            isShutdown = true
            playback.stop()
            val engine = textToSpeech
            textToSpeech = null
            runCatching { engine?.stop() }
            runCatching { engine?.shutdown() }
            mutableState.value = playback.setReady(false)
        }
    }

    private fun initialize(status: Int) {
        if (isShutdown) return
        val engine = textToSpeech
        if (status != TextToSpeech.SUCCESS || engine == null) {
            mutableState.value = playback.setReady(false)
            return
        }

        val languageStatus = runCatching { engine.setLanguage(Locale("zh", "CN")) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val ready = languageStatus >= TextToSpeech.LANG_AVAILABLE
        if (ready) {
            runCatching {
                engine.setSpeechRate(currentSpeechRate)
                engine.setPitch(1.03f)
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = onUtteranceStart(utteranceId)
                override fun onDone(utteranceId: String?) = onUtteranceFinished(utteranceId, null, false)
                override fun onError(utteranceId: String?) = onUtteranceFinished(utteranceId, null, true)
                override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceFinished(utteranceId, errorCode, true)
            })
        }
        playback.setSpeechRate(currentSpeechRate)
        mutableState.value = playback.setReady(ready)
    }

    private fun onUtteranceStart(utteranceId: String?) {
        onMain {
            if (!isShutdown && utteranceId != null) {
                mutableState.value = playback.onStart(utteranceId)
            }
        }
    }

    private fun onUtteranceFinished(utteranceId: String?, errorCode: Int?, failed: Boolean) {
        onMain {
            if (!isShutdown && utteranceId != null) {
                mutableState.value = if (failed) playback.onError(utteranceId, errorCode) else playback.onDone(utteranceId)
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
