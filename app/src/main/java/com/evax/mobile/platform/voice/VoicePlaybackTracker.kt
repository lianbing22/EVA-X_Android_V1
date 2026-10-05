package com.evax.mobile.platform.voice

import com.evax.mobile.presentation.VoicePlaybackEvent
import com.evax.mobile.presentation.VoicePlaybackState

/** 由 TTS 真实回调推进播放状态，入队和播放分开记录。仅从控制器主线程调用。 */
internal data class DeferredSpeech(val utteranceId: String, val text: String, val flush: Boolean)

internal class VoicePlaybackTracker(
    private val maxPendingUtterances: Int = 24,
    private val maxPendingCharacters: Int = 16_000,
) {
    private val queued = linkedSetOf<String>()
    private val playing = mutableSetOf<String>()
    private val deferred = mutableListOf<DeferredSpeech>()
    var state = VoicePlaybackState()
        private set

    fun setReady(ready: Boolean): VoicePlaybackState {
        state = state.copy(isReady = ready)
        return state
    }

    fun setSpeechRate(rate: Float): VoicePlaybackState {
        state = state.copy(speechRate = rate)
        return state
    }

    fun enqueue(utteranceId: String, flush: Boolean): VoicePlaybackState {
        if (flush) {
            queued.clear()
            playing.clear()
            deferred.clear()
        }
        queued.add(utteranceId)
        state = state.copy(isSpeaking = playing.isNotEmpty(), queuedCount = queued.size)
        return state
    }

    fun defer(utteranceId: String, text: String, flush: Boolean): VoicePlaybackState {
        enqueue(utteranceId, flush)
        if (deferred.size >= maxPendingUtterances || deferred.sumOf { it.text.length } + text.length > maxPendingCharacters) {
            return onError(utteranceId, ERROR_PENDING_LIMIT)
        }
        deferred.add(DeferredSpeech(utteranceId, text, flush))
        return state
    }

    fun takeDeferred(): List<DeferredSpeech> = deferred.toList().also { deferred.clear() }

    fun initializationFailed(errorCode: Int): VoicePlaybackState {
        state = state.copy(isReady = false)
        if (queued.isEmpty()) return state
        queued.clear()
        playing.clear()
        deferred.clear()
        return event(VoicePlaybackEvent.ERROR, null, errorCode)
    }

    fun onStart(utteranceId: String): VoicePlaybackState {
        if (utteranceId !in queued || !playing.add(utteranceId)) return state
        return event(VoicePlaybackEvent.STARTED, utteranceId)
    }

    fun onDone(utteranceId: String): VoicePlaybackState = finish(utteranceId, VoicePlaybackEvent.DONE, null)

    fun onError(utteranceId: String, errorCode: Int?): VoicePlaybackState = finish(utteranceId, VoicePlaybackEvent.ERROR, errorCode)

    fun stop(): VoicePlaybackState {
        if (queued.isEmpty() && playing.isEmpty()) return state
        queued.clear()
        playing.clear()
        deferred.clear()
        return event(VoicePlaybackEvent.STOPPED, null)
    }

    private fun finish(utteranceId: String, kind: VoicePlaybackEvent, errorCode: Int?): VoicePlaybackState {
        if (!queued.remove(utteranceId)) return state
        playing.remove(utteranceId)
        deferred.removeAll { it.utteranceId == utteranceId }
        return event(kind, utteranceId, errorCode)
    }

    private fun event(kind: VoicePlaybackEvent, utteranceId: String?, errorCode: Int? = null): VoicePlaybackState {
        state = state.copy(
            isSpeaking = playing.isNotEmpty(),
            queuedCount = queued.size,
            playbackEvent = kind,
            utteranceId = utteranceId,
            eventSequence = state.eventSequence + 1,
            errorCode = errorCode,
        )
        return state
    }

    companion object {
        const val ERROR_PENDING_LIMIT = -10_001
        const val ERROR_INITIALIZATION = -10_002
        const val ERROR_LANGUAGE_UNAVAILABLE = -10_003
    }
}
