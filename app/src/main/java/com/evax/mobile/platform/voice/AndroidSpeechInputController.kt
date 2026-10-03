package com.evax.mobile.platform.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.evax.mobile.domain.SpeechInputFailure
import java.util.concurrent.atomic.AtomicBoolean

class AndroidSpeechInputController(context: Context) : SpeechInputController {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var activeDelivery: AtomicBoolean? = null

    @Volatile
    private var destroyed = false

    override fun start(
        onResult: (String) -> Unit,
        onFailure: (SpeechInputFailure) -> Unit,
    ) {
        onMain {
            if (destroyed) {
                onFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)
                return@onMain
            }
            cancelRecognizer()
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                onFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)
                return@onMain
            }

            val instance = try {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } catch (_: RuntimeException) {
                onFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)
                return@onMain
            }
            val delivery = AtomicBoolean(false)
            recognizer = instance
            activeDelivery = delivery

            fun finishWithFailure(failure: SpeechInputFailure) {
                if (delivery.compareAndSet(false, true)) onFailure(failure)
                release(instance, delivery)
            }

            instance.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    finishWithFailure(SpeechErrorMapper.map(error))
                }

                override fun onResults(results: Bundle?) {
                    if (!delivery.compareAndSet(false, true)) return
                    val recognizedText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (recognizedText.isBlank()) {
                        onFailure(SpeechInputFailure.NO_MATCH)
                    } else {
                        onResult(recognizedText)
                    }
                    release(instance, delivery)
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            try {
                instance.startListening(intent)
            } catch (_: SecurityException) {
                finishWithFailure(SpeechInputFailure.PERMISSION_DENIED)
            } catch (_: RuntimeException) {
                finishWithFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)
            }
        }
    }

    override fun cancel() {
        onMain { cancelRecognizer() }
    }

    override fun destroy() {
        onMain {
            destroyed = true
            cancelRecognizer()
        }
    }

    private fun cancelRecognizer() {
        val current = recognizer
        val delivery = activeDelivery
        recognizer = null
        activeDelivery = null
        delivery?.set(true)
        if (current != null) {
            runCatching { current.cancel() }
            current.destroy()
        }
    }

    private fun release(instance: SpeechRecognizer, delivery: AtomicBoolean) {
        if (recognizer === instance && activeDelivery === delivery) {
            recognizer = null
            activeDelivery = null
            instance.destroy()
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
