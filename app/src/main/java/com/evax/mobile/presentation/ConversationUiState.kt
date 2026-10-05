package com.evax.mobile.presentation

import com.evax.mobile.domain.AssistantSource

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

enum class ListeningStage { NONE, PREPARING, READY, SPEAKING, RECOGNIZING }

enum class AvatarFeedbackKind {
    REPLY_READY, TASK_SUCCEEDED, DEMO_SUCCEEDED, NEEDS_ATTENTION,
    FAILED, UNCERTAIN, CANCELLED, SPEECH_NOT_UNDERSTOOD,
}

data class AvatarFeedback(val kind: AvatarFeedbackKind, val eventId: Long)

enum class VoicePlaybackEvent { NONE, STARTED, DONE, ERROR, STOPPED }

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
    val playbackEvent: VoicePlaybackEvent = VoicePlaybackEvent.NONE,
    val utteranceId: String? = null,
    val eventSequence: Long = 0L,
    val queuedCount: Int = 0,
    val errorCode: Int? = null,
)

data class ConversationUiState(
    val draft: String = "",
    val messages: List<ConversationMessage> = emptyList(),
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val currentStep: String? = null,
    val progressSteps: List<String> = emptyList(),
    val completedProgressSteps: List<String> = emptyList(),
    val progressIndex: Int = 0,
    val progressTotal: Int = 0,
    val streamingReply: String? = null,
    val lastLatencyMs: Long? = null,
    val gatewayLabel: String = "未确认连接",
    val source: AssistantSource = AssistantSource.UNCONFIRMED,
    val notice: String? = null,
    val canRetryTask: Boolean = true,
    val isProcessing: Boolean = false,
    val voicePlayback: VoicePlaybackState = VoicePlaybackState(),
    val listeningStage: ListeningStage = ListeningStage.NONE,
    val listeningSessionId: Long = 0L,
    val avatarFeedback: AvatarFeedback? = null,
    val currentTaskId: String? = null,
    val pendingAttention: Boolean = false,
)
