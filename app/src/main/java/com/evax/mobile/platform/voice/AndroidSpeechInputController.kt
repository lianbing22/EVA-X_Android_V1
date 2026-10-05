package com.evax.mobile.platform.voice

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.evax.mobile.domain.SpeechInputFailure
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

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
        onPartialResult: (String) -> Unit,
        onRmsChanged: (Float) -> Unit,
    ) {
        onMain {
            if (destroyed) {
                onFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)
                return@onMain
            }
            cancelRecognizer()

            val candidates = discoverCandidateComponents()
            if (candidates.isEmpty()) {
                onFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)
                return@onMain
            }

            tryStartCandidate(
                candidates = candidates,
                index = 0,
                onResult = onResult,
                onFailure = onFailure,
                onPartialResult = onPartialResult,
                onRmsChanged = onRmsChanged,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun discoverCandidateComponents(): List<ComponentName?> {
        val result = mutableListOf<ComponentName?>()
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            result.add(null) // Default system recognizer
        }
        try {
            val services = appContext.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE),
                0,
            )
            services.forEach { resolveInfo ->
                val si = resolveInfo.serviceInfo
                if (si != null && !si.packageName.isNullOrBlank() && !si.name.isNullOrBlank()) {
                    val cn = ComponentName(si.packageName, si.name)
                    if (!result.contains(cn)) {
                        result.add(cn)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }

    private fun tryStartCandidate(
        candidates: List<ComponentName?>,
        index: Int,
        onResult: (String) -> Unit,
        onFailure: (SpeechInputFailure) -> Unit,
        onPartialResult: (String) -> Unit,
        onRmsChanged: (Float) -> Unit,
    ) {
        if (destroyed || index >= candidates.size) {
            onFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)
            return
        }

        val component = candidates[index]
        val instance = try {
            if (component == null) {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext, component)
            }
        } catch (_: Throwable) {
            tryStartCandidate(candidates, index + 1, onResult, onFailure, onPartialResult, onRmsChanged)
            return
        }

        val delivery = AtomicBoolean(false)
        var lastPartialText = ""
        recognizer = instance
        activeDelivery = delivery

        fun fallbackOrFail(failure: SpeechInputFailure) {
            release(instance, delivery)
            if (index + 1 < candidates.size &&
                (failure == SpeechInputFailure.SERVICE_UNAVAILABLE || failure == SpeechInputFailure.UNKNOWN)
            ) {
                tryStartCandidate(candidates, index + 1, onResult, onFailure, onPartialResult, onRmsChanged)
            } else if (delivery.compareAndSet(false, true)) {
                onFailure(failure)
            }
        }

        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val stable = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    .orEmpty()
                val unstable = partialResults
                    ?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                    ?.firstOrNull { it.isNotBlank() }
                    .orEmpty()
                val combined = when {
                    stable.isNotBlank() && unstable.isNotBlank() && !stable.endsWith(unstable) -> "$stable$unstable"
                    stable.isNotBlank() -> stable
                    else -> unstable
                }.trim()
                if (combined.isNotBlank()) {
                    lastPartialText = combined
                    onPartialResult(combined)
                }
            }

            override fun onError(error: Int) {
                // If partial results already captured real speech before end-of-speech timeout/no-match, deliver it!
                if (lastPartialText.isNotBlank() &&
                    (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                ) {
                    if (delivery.compareAndSet(false, true)) {
                        onResult(lastPartialText)
                    }
                    release(instance, delivery)
                    return
                }
                fallbackOrFail(SpeechErrorMapper.map(error))
            }

            override fun onResults(results: Bundle?) {
                if (!delivery.compareAndSet(false, true)) return
                val recognizedText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.ifBlank { lastPartialText }
                    ?: lastPartialText

                if (recognizedText.isBlank()) {
                    onFailure(SpeechInputFailure.NO_MATCH)
                } else {
                    onResult(recognizedText)
                }
                release(instance, delivery)
            }
        })

        val localeTag = Locale.getDefault().toLanguageTag().ifBlank { "zh-CN" }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }

        try {
            instance.startListening(intent)
        } catch (_: SecurityException) {
            if (delivery.compareAndSet(false, true)) {
                onFailure(SpeechInputFailure.PERMISSION_DENIED)
            }
            release(instance, delivery)
        } catch (_: Throwable) {
            fallbackOrFail(SpeechInputFailure.SERVICE_UNAVAILABLE)
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
            runCatching { current.destroy() }
        }
    }

    private fun release(instance: SpeechRecognizer, delivery: AtomicBoolean) {
        if (recognizer === instance && activeDelivery === delivery) {
            recognizer = null
            activeDelivery = null
            runCatching { instance.destroy() }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}

/**
 * Real hardware microphone level monitor using [AudioRecord] when fallback live dictation sheet is open.
 */
class RealAudioMicMonitor {
    @Volatile
    private var running = false
    private var workerThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(onLevel: (Float) -> Unit) {
        if (running) return
        running = true
        val mainHandler = Handler(Looper.getMainLooper())
        workerThread = Thread {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(1280)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf,
                )
            } catch (_: Throwable) {
                null
            } ?: return@Thread

            try {
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buffer = ShortArray(minBuf / 2)
                    while (running) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                val s = buffer[i].toDouble()
                                sum += s * s
                            }
                            val rms = sqrt(sum / read)
                            val normalized = (rms / 4500.0).toFloat().coerceIn(0.05f, 1.0f)
                            mainHandler.post { onLevel(normalized) }
                        }
                        Thread.sleep(50L)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        workerThread?.interrupt()
        workerThread = null
    }
}
