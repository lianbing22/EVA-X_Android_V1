package com.evax.mobile.domain

sealed interface AssistantEvent {
    data class Progress(
        val step: String,
        val index: Int,
        val total: Int,
    ) : AssistantEvent

    data class StreamDelta(
        val delta: String,
        val partialText: String,
    ) : AssistantEvent

    data class SpeakSentence(
        val sentence: String,
        val isFirst: Boolean,
        val latencyMs: Long = 0L,
    ) : AssistantEvent

    data class Completed(val result: AssistantResult) : AssistantEvent
}
