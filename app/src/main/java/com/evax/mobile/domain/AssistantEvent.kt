package com.evax.mobile.domain

sealed interface AssistantEvent {
    data class Progress(
        val step: String,
        val index: Int,
        val total: Int,
    ) : AssistantEvent

    data class Completed(val result: AssistantResult) : AssistantEvent
}
