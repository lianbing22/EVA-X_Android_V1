package com.evax.mobile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evax.mobile.domain.AssistantEngine
import com.evax.mobile.domain.AssistantEvent
import com.evax.mobile.domain.AssistantSource
import com.evax.mobile.domain.GatewayException
import com.evax.mobile.domain.SpeechInputFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val engine: AssistantEngine,
    onSpeakChunk: ((sentence: String, isFirst: Boolean) -> Unit)? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ConversationUiState())
    val uiState = mutableUiState.asStateFlow()

    private var nextMessageId = 1L
    private var taskGeneration = 0L
    private var processingJob: Job? = null
    private var completedReturnJob: Job? = null
    private var speechCallback = onSpeakChunk

    fun setSpeechCallback(callback: ((sentence: String, isFirst: Boolean) -> Unit)?) {
        speechCallback = callback
    }

    fun onDraftChanged(text: String) {
        mutableUiState.update { it.copy(draft = text) }
    }

    fun submitPrompt(text: String? = null) {
        val current = mutableUiState.value
        if (current.isProcessing) return

        val prompt = (text ?: current.draft).trim()
        if (prompt.isBlank()) return

        completedReturnJob?.cancel()
        val taskId = ++taskGeneration
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
                completedProgressSteps = emptyList(),
                progressIndex = 0,
                progressTotal = 0,
                streamingReply = null,
                lastLatencyMs = null,
                source = AssistantSource.UNCONFIRMED,
                gatewayLabel = "未确认连接",
                notice = null,
                canRetryTask = true,
                isProcessing = true,
            )
        }

        processingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var receivedResult = false
            var spokeStreamingSentence = false
            try {
                engine.respond(prompt).collect { event ->
                    // 取消后的阻塞 I/O 或迟到回调不能覆盖新任务。
                    if (!isCurrentTask(taskId)) return@collect
                    when (event) {
                        is AssistantEvent.SourceChanged -> {
                            mutableUiState.update { state ->
                                if (state.source == event.source) state.copy(gatewayLabel = event.label) else state.copy(
                                    source = event.source,
                                    gatewayLabel = event.label,
                                    currentStep = null,
                                    progressSteps = emptyList(),
                                    completedProgressSteps = emptyList(),
                                    progressIndex = 0,
                                    progressTotal = 0,
                                    streamingReply = null,
                                )
                            }
                        }

                        is AssistantEvent.Progress -> {
                            mutableUiState.update { state ->
                                val finishedStep = state.currentStep.takeIf {
                                    event.index > state.progressIndex
                                }
                                state.copy(
                                    phase = AssistantPhase.EXECUTING,
                                    currentStep = event.step,
                                    progressIndex = event.index,
                                    progressTotal = event.total,
                                    completedProgressSteps = if (finishedStep == null) {
                                        state.completedProgressSteps
                                    } else {
                                        state.completedProgressSteps + finishedStep
                                    },
                                    progressSteps = if (state.progressSteps.lastOrNull() == event.step) {
                                        state.progressSteps
                                    } else {
                                        state.progressSteps + event.step
                                    },
                                )
                            }
                        }

                        is AssistantEvent.StreamDelta -> {
                            mutableUiState.update { state ->
                                state.copy(
                                    phase = AssistantPhase.EXECUTING,
                                    streamingReply = event.partialText,
                                )
                            }
                        }

                        is AssistantEvent.SpeakSentence -> {
                            spokeStreamingSentence = true
                            speechCallback?.invoke(event.sentence, event.isFirst)
                            if (event.isFirst && event.latencyMs > 0L) {
                                mutableUiState.update { state ->
                                    state.copy(lastLatencyMs = event.latencyMs)
                                }
                            }
                        }

                        is AssistantEvent.Completed -> {
                            receivedResult = true
                            if (!spokeStreamingSentence && event.result.text.isNotBlank()) {
                                speechCallback?.invoke(event.result.text, true)
                            }
                            if (!isCurrentTask(taskId)) return@collect
                            val assistantMessage = ConversationMessage(
                                id = nextMessageId++,
                                role = MessageRole.ASSISTANT,
                                text = event.result.text,
                                isSample = event.result.isSample,
                                sampleLabel = event.result.sampleLabel,
                                followUps = event.result.followUps,
                            )
                            mutableUiState.update { state ->
                                val resultSource = event.result.source.takeUnless {
                                    it == AssistantSource.UNCONFIRMED
                                } ?: state.source
                                state.copy(
                                    messages = state.messages + assistantMessage,
                                    phase = AssistantPhase.COMPLETED,
                                    completedProgressSteps = state.currentStep?.let {
                                        state.completedProgressSteps + it
                                    } ?: state.completedProgressSteps,
                                    currentStep = null,
                                    streamingReply = null,
                                    source = resultSource,
                                    gatewayLabel = if (resultSource != state.source) {
                                        sourceLabel(resultSource)
                                    } else {
                                        state.gatewayLabel
                                    },
                                    notice = null,
                                    isProcessing = false,
                                )
                            }
                            scheduleReturnToIdle(taskId)
                        }
                    }
                }
                if (!receivedResult && isCurrentTask(taskId)) {
                    showEngineError("任务没有返回结果，请重试。", taskId)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (isCurrentTask(taskId)) {
                    showEngineError(
                        if (failure is GatewayException) failure.userMessage else "刚才的任务中断了，请重试。",
                        taskId,
                        canRetryTask = (failure as? GatewayException)?.canRetryTask ?: true,
                    )
                }
            } finally {
                if (taskGeneration == taskId) processingJob = null
            }
        }
        processingJob?.start()
    }

    fun cancelProcessing() {
        if (!mutableUiState.value.isProcessing) return
        ++taskGeneration
        processingJob?.cancel()
        processingJob = null
        completedReturnJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                phase = AssistantPhase.IDLE,
                currentStep = null,
                streamingReply = null,
                notice = "已停止接收。电脑端任务可能仍在继续。",
                isProcessing = false,
            )
        }
    }

    fun onListeningStarted() {
        if (mutableUiState.value.isProcessing) return
        completedReturnJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
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
        if (mutableUiState.value.isProcessing) return
        if (text.isBlank()) {
            completedReturnJob?.cancel()
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
        if (mutableUiState.value.isProcessing) return
        completedReturnJob?.cancel()
        val message = when (failure) {
            SpeechInputFailure.PERMISSION_DENIED -> "麦克风权限未开启，可继续输入文字。"
            SpeechInputFailure.SERVICE_UNAVAILABLE -> "设备没有可用的语音识别服务，请改用文字输入。"
            SpeechInputFailure.NO_MATCH -> "没有听清，再试一次"
            SpeechInputFailure.NETWORK -> "语音识别服务暂时不可用，请改用文字输入。"
            SpeechInputFailure.UNKNOWN -> "语音识别暂不可用，请改用文字输入。"
        }
        mutableUiState.update { state ->
            state.copy(
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
        mutableUiState.update { state ->
            state.copy(
                notice = null,
                phase = if (state.phase == AssistantPhase.ERROR) AssistantPhase.IDLE else state.phase,
            )
        }
    }

    private fun isCurrentTask(taskId: Long): Boolean =
        taskGeneration == taskId && mutableUiState.value.isProcessing

    private fun showEngineError(message: String, taskId: Long, canRetryTask: Boolean = true) {
        if (!isCurrentTask(taskId)) return
        completedReturnJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                phase = AssistantPhase.ERROR,
                currentStep = null,
                streamingReply = null,
                notice = message,
                canRetryTask = canRetryTask,
                isProcessing = false,
            )
        }
    }

    private fun scheduleReturnToIdle(taskId: Long) {
        completedReturnJob?.cancel()
        completedReturnJob = viewModelScope.launch {
            delay(2_000L)
            mutableUiState.update { state ->
                if (taskGeneration == taskId && state.phase == AssistantPhase.COMPLETED && !state.isProcessing) {
                    state.copy(phase = AssistantPhase.IDLE)
                } else {
                    state
                }
            }
        }
    }

    private fun sourceLabel(source: AssistantSource): String = when (source) {
        AssistantSource.UNCONFIRMED -> "未确认连接"
        AssistantSource.PC_GATEWAY -> "电脑网关"
        AssistantSource.LOCAL_DEMO -> "本地演示"
    }
}
