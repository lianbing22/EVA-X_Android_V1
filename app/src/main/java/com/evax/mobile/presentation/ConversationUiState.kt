package com.evax.mobile.presentation

enum class AssistantPhase {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING,
    COMPLETED,
    ERROR,
}

enum class MessageRole {
    USER,
    ASSISTANT,
}

data class ConversationMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val isSample: Boolean = false,
    val sampleLabel: String = "演示数据",
    val followUps: List<String> = emptyList(),
)

data class VoicePlaybackState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val speechRate: Float = 1.25f,
)

data class ConversationUiState(
    val draft: String = "",
    val messages: List<ConversationMessage> = emptyList(),
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val currentStep: String? = null,
    val progressSteps: List<String> = emptyList(),
    val streamingReply: String? = null,
    val lastLatencyMs: Long? = null,
    val gatewayLabel: String = "PC · DSH+WorkBuddy",
    val notice: String? = null,
    val isProcessing: Boolean = false,
    val voicePlayback: VoicePlaybackState = VoicePlaybackState(),
)
