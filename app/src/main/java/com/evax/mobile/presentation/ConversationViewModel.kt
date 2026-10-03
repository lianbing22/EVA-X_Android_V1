package com.evax.mobile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evax.mobile.domain.AssistantEngine
import com.evax.mobile.domain.AssistantEvent
import com.evax.mobile.domain.SpeechInputFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val engine: AssistantEngine,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ConversationUiState())
    val uiState = mutableUiState.asStateFlow()

    private var nextMessageId = 1L

    fun onDraftChanged(text: String) {
        mutableUiState.update { it.copy(draft = text) }
    }

    fun submitPrompt(text: String? = null) {
        val current = mutableUiState.value
        if (current.isProcessing) return

        val prompt = (text ?: current.draft).trim()
        if (prompt.isBlank()) return

        val userMessage = ConversationMessage(
            id = nextMessageId++,
            role = MessageRole.USER,
            text = prompt,
        )
        mutableUiState.update {
            it.copy(
                draft = "",
                messages = it.messages + userMessage,
                phase = AssistantPhase.THINKING,
                currentStep = null,
                progressSteps = emptyList(),
                notice = null,
                isProcessing = true,
            )
        }

        viewModelScope.launch {
            var receivedResult = false
            try {
                engine.respond(prompt).collect { event ->
                    when (event) {
                        is AssistantEvent.Progress -> {
                            mutableUiState.update { state ->
                                if (!state.isProcessing) state else state.copy(
                                    phase = AssistantPhase.EXECUTING,
                                    currentStep = event.step,
                                    progressSteps = if (state.progressSteps.lastOrNull() == event.step) {
                                        state.progressSteps
                                    } else {
                                        state.progressSteps + event.step
                                    },
                                )
                            }
                        }

                        is AssistantEvent.Completed -> {
                            receivedResult = true
                            mutableUiState.update { state ->
                                if (!state.isProcessing) state else state.copy(
                                    messages = state.messages + ConversationMessage(
                                        id = nextMessageId++,
                                        role = MessageRole.ASSISTANT,
                                        text = event.result.text,
                                        isSample = true,
                                        followUps = event.result.followUps,
                                    ),
                                    phase = AssistantPhase.COMPLETED,
                                    currentStep = null,
                                    notice = null,
                                    isProcessing = false,
                                )
                            }
                        }
                    }
                }
                if (!receivedResult && mutableUiState.value.isProcessing) {
                    showEngineError("演示任务没有返回结果，请重试。")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                showEngineError("刚才的演示任务中断了，请重试。")
            }
        }
    }

    fun onListeningStarted() {
        mutableUiState.update { state ->
            if (state.isProcessing) state else state.copy(
                phase = AssistantPhase.LISTENING,
                notice = null,
            )
        }
    }

    fun onListeningCancelled() {
        mutableUiState.update { state ->
            if (state.phase != AssistantPhase.LISTENING || state.isProcessing) state else state.copy(
                phase = AssistantPhase.IDLE,
                currentStep = null,
                notice = null,
            )
        }
    }

    fun onSpeechResult(text: String) {
        if (text.isBlank()) {
            mutableUiState.update { state ->
                state.copy(
                    phase = AssistantPhase.IDLE,
                    notice = "没有听清，再试一次",
                )
            }
            return
        }
        submitPrompt(text)
    }

    fun onSpeechFailure(failure: SpeechInputFailure) {
        val message = when (failure) {
            SpeechInputFailure.PERMISSION_DENIED -> "麦克风权限未开启，可继续输入文字。"
            SpeechInputFailure.SERVICE_UNAVAILABLE -> "设备没有可用的语音识别服务，请改用文字输入。"
            SpeechInputFailure.NO_MATCH -> "没有听清，再试一次"
            SpeechInputFailure.NETWORK -> "语音识别服务暂时不可用，请改用文字输入。"
            SpeechInputFailure.UNKNOWN -> "语音识别暂不可用，请改用文字输入。"
        }
        mutableUiState.update { state ->
            if (state.isProcessing) state else state.copy(
                phase = AssistantPhase.IDLE,
                currentStep = null,
                notice = message,
            )
        }
    }

    fun onVoicePlaybackChanged(state: VoicePlaybackState) {
        mutableUiState.update { it.copy(voicePlayback = state) }
    }

    fun clearNotice() {
        mutableUiState.update { it.copy(notice = null) }
    }

    private fun showEngineError(message: String) {
        mutableUiState.update { state ->
            state.copy(
                phase = AssistantPhase.ERROR,
                currentStep = null,
                notice = message,
                isProcessing = false,
            )
        }
    }
}
