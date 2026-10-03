package com.evax.mobile.platform.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.evax.mobile.presentation.VoicePlaybackState
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidTextToSpeechController(context: Context) : SpeechOutputController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(VoicePlaybackState(speechRate = 1.25f))
    private val utteranceSequence = AtomicLong(0)
    private val activeUtterances = Collections.synchronizedSet( linkedSetOf<String>() )

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

            if (flush) {
                activeUtterances.clear()
            }
            val utteranceId = "eva-${utteranceSequence.incrementAndGet()}"
            activeUtterances.add(utteranceId)
            mutableState.update { it.copy(isSpeaking = true, speechRate = currentSpeechRate) }

            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = engine.speak(cleaned, queueMode, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                activeUtterances.remove(utteranceId)
                if (activeUtterances.isEmpty()) {
                    mutableState.update { it.copy(isSpeaking = false) }
                }
            }
        }
    }

    override fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.8f, 2.0f)
        currentSpeechRate = clamped
        onMain {
            runCatching { textToSpeech?.setSpeechRate(clamped) }
            mutableState.update { it.copy(speechRate = clamped) }
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
            activeUtterances.clear()
            runCatching { textToSpeech?.stop() }
            mutableState.update { it.copy(isSpeaking = false) }
        }
    }

    override fun shutdown() {
        onMain {
            if (isShutdown) return@onMain
            isShutdown = true
            activeUtterances.clear()
            val engine = textToSpeech
            textToSpeech = null
            runCatching { engine?.stop() }
            engine?.shutdown()
            mutableState.value = VoicePlaybackState(speechRate = currentSpeechRate)
        }
    }

    private fun initialize(status: Int) {
        if (isShutdown) return
        val engine = textToSpeech
        if (status != TextToSpeech.SUCCESS || engine == null) {
            mutableState.value = VoicePlaybackState(speechRate = currentSpeechRate)
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
                override fun onDone(utteranceId: String?) = onUtteranceFinished(utteranceId)
                override fun onError(utteranceId: String?) = onUtteranceFinished(utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceFinished(utteranceId)
            })
        }
        mutableState.value = VoicePlaybackState(isReady = ready, speechRate = currentSpeechRate)
    }

    private fun onUtteranceStart(utteranceId: String?) {
        onMain {
            if (!isShutdown && utteranceId != null && activeUtterances.contains(utteranceId)) {
                mutableState.update { it.copy(isSpeaking = true) }
            }
        }
    }

    private fun onUtteranceFinished(utteranceId: String?) {
        onMain {
            if (utteranceId != null) {
                activeUtterances.remove(utteranceId)
            }
            if (!isShutdown && activeUtterances.isEmpty()) {
                mutableState.update { it.copy(isSpeaking = false) }
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
