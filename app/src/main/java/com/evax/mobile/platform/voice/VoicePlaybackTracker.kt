package com.evax.mobile.platform.voice

import com.evax.mobile.presentation.VoicePlaybackEvent
import com.evax.mobile.presentation.VoicePlaybackState

/** 由 TTS 真实回调推进播放状态，入队和播放分开记录。仅从控制器主线程调用。 */
internal class VoicePlaybackTracker {
    private val queued = linkedSetOf<String>()
    private val playing = mutableSetOf<String>()
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
        }
        queued.add(utteranceId)
        state = state.copy(isSpeaking = playing.isNotEmpty(), queuedCount = queued.size)
        return state
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
        return event(VoicePlaybackEvent.STOPPED, null)
    }

    private fun finish(utteranceId: String, kind: VoicePlaybackEvent, errorCode: Int?): VoicePlaybackState {
        if (!queued.remove(utteranceId)) return state
        playing.remove(utteranceId)
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
}
