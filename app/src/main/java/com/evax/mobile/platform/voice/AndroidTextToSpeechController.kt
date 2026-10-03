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
import kotlinx.coroutines.flow.update

class AndroidTextToSpeechController(context: Context) : SpeechOutputController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(VoicePlaybackState())
    private val utteranceSequence = AtomicLong(0)

    @Volatile
    private var textToSpeech: TextToSpeech? = null

    @Volatile
    private var activeUtteranceId: String? = null

    @Volatile
    private var isShutdown = false

    override val state = mutableState.asStateFlow()

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            onMain { initialize(status) }
        }
    }

    override fun speak(text: String) {
        onMain {
            val engine = textToSpeech ?: return@onMain
            if (isShutdown || !mutableState.value.isReady || text.isBlank()) return@onMain
            val utteranceId = "eva-${utteranceSequence.incrementAndGet()}"
            activeUtteranceId = utteranceId
            val result = engine.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                activeUtteranceId = null
                mutableState.update { it.copy(isSpeaking = false) }
            }
        }
    }

    override fun stop() {
        onMain {
            activeUtteranceId = null
            runCatching { textToSpeech?.stop() }
            mutableState.update { it.copy(isSpeaking = false) }
        }
    }

    override fun shutdown() {
        onMain {
            if (isShutdown) return@onMain
            isShutdown = true
            activeUtteranceId = null
            val engine = textToSpeech
            textToSpeech = null
            runCatching { engine?.stop() }
            engine?.shutdown()
            mutableState.value = VoicePlaybackState()
        }
    }

    private fun initialize(status: Int) {
        if (isShutdown) return
        val engine = textToSpeech
        if (status != TextToSpeech.SUCCESS || engine == null) {
            mutableState.value = VoicePlaybackState()
            return
        }

        val languageStatus = runCatching { engine.setLanguage(Locale("zh", "CN")) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val ready = languageStatus >= TextToSpeech.LANG_AVAILABLE
        if (ready) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = updateSpeaking(utteranceId, true)
                override fun onDone(utteranceId: String?) = updateSpeaking(utteranceId, false)
                override fun onError(utteranceId: String?) = updateSpeaking(utteranceId, false)
                override fun onError(utteranceId: String?, errorCode: Int) = updateSpeaking(utteranceId, false)
            })
        }
        mutableState.value = VoicePlaybackState(isReady = ready)
    }

    private fun updateSpeaking(utteranceId: String?, isSpeaking: Boolean) {
        onMain {
            if (utteranceId != null && utteranceId == activeUtteranceId && !isShutdown) {
                mutableState.update { it.copy(isSpeaking = isSpeaking) }
                if (!isSpeaking) activeUtteranceId = null
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
