package com.evax.mobile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evax.mobile.domain.AssistantEngine
import com.evax.mobile.domain.AssistantEvent
import com.evax.mobile.domain.AssistantOutcome
import com.evax.mobile.domain.AssistantResult
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
    private var nextFeedbackId = 0L
    private var nextListeningSessionId = 0L
    private var activeListeningSessionId: Long? = null
    private var currentRequestId: String? = null

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
        activeListeningSessionId = null
        currentRequestId = null
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
                listeningStage = ListeningStage.NONE,
                avatarFeedback = null,
                currentTaskId = null,
                pendingAttention = false,
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
                        is AssistantEvent.RequestStarted -> {
                            if (currentRequestId == null) currentRequestId = event.requestId
                            else if (currentRequestId != event.requestId) {
                                showEngineError("电脑请求标识发生变化，请先在电脑端确认状态。", taskId, false, AvatarFeedbackKind.UNCERTAIN)
                            }
                        }
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
                            if (event.requestId != null && event.requestId != currentRequestId) return@collect
                            if (event.taskId != null && mutableUiState.value.currentTaskId != null &&
                                event.taskId != mutableUiState.value.currentTaskId
                            ) return@collect
                            mutableUiState.update { state ->
                                val finishedStep = state.currentStep.takeIf {
                                    event.index > state.progressIndex
                                }
                                state.copy(
                                    phase = AssistantPhase.EXECUTING,
                                    currentTaskId = event.taskId?.takeIf {
                                        currentRequestId != null && event.requestId == currentRequestId
                                    } ?: state.currentTaskId,
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
                            deliverSpeech(event.sentence, event.isFirst)
                            if (event.isFirst && event.latencyMs > 0L) {
                                mutableUiState.update { state ->
                                    state.copy(lastLatencyMs = event.latencyMs)
                                }
                            }
                        }

                        is AssistantEvent.Completed -> {
                            if (event.result.requestId != null && event.result.requestId != currentRequestId) {
                                showEngineError("电脑回复与当前请求不匹配，请先在电脑端确认状态。", taskId, false, AvatarFeedbackKind.UNCERTAIN)
                                return@collect
                            }
                            if (event.result.taskId != null && mutableUiState.value.currentTaskId != null &&
                                event.result.taskId != mutableUiState.value.currentTaskId
                            ) {
                                showEngineError("收到另一项电脑任务的结果，请先在电脑端确认状态。", taskId, false, AvatarFeedbackKind.UNCERTAIN)
                                return@collect
                            }
                            receivedResult = true
                            val resultKind = resultFeedback(event.result)
                            if (!isCurrentTask(taskId)) return@collect
                            val resultSource = event.result.source.takeUnless {
                                it == AssistantSource.UNCONFIRMED
                            } ?: mutableUiState.value.source
                            val assistantMessage = ConversationMessage(
                                id = nextMessageId++,
                                role = MessageRole.ASSISTANT,
                                text = event.result.text,
                                isSample = event.result.isSample || resultSource == AssistantSource.LOCAL_DEMO,
                                sampleLabel = if (resultSource == AssistantSource.LOCAL_DEMO) "演示数据" else event.result.sampleLabel,
                                followUps = event.result.followUps,
                            )
                            mutableUiState.update { state ->
                                state.copy(
                                    messages = state.messages + assistantMessage,
                                    phase = if (resultKind in setOf(AvatarFeedbackKind.FAILED, AvatarFeedbackKind.UNCERTAIN, AvatarFeedbackKind.NEEDS_ATTENTION)) {
                                        AssistantPhase.ERROR
                                    } else AssistantPhase.COMPLETED,
                                    completedProgressSteps = state.currentStep?.takeIf {
                                        resultKind == AvatarFeedbackKind.TASK_SUCCEEDED || resultKind == AvatarFeedbackKind.DEMO_SUCCEEDED
                                    }?.let {
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
                                    notice = resultNotice(resultKind) ?: state.notice.takeIf {
                                        state.voicePlayback.playbackEvent == VoicePlaybackEvent.ERROR
                                    },
                                    canRetryTask = resultKind !in setOf(AvatarFeedbackKind.UNCERTAIN, AvatarFeedbackKind.NEEDS_ATTENTION, AvatarFeedbackKind.REPLY_READY),
                                    isProcessing = false,
                                    avatarFeedback = feedback(resultKind),
                                    pendingAttention = resultKind == AvatarFeedbackKind.UNCERTAIN || resultKind == AvatarFeedbackKind.NEEDS_ATTENTION,
                                )
                            }
                            scheduleReturnToIdle(taskId)
                            if (!spokeStreamingSentence && event.result.text.isNotBlank()) {
                                deliverSpeech(event.result.text, true)
                            }
                        }
                    }
                }
                if (!receivedResult && isCurrentTask(taskId)) {
                    showEngineError("未收到明确结果，请先查看电脑端状态，避免重复执行。", taskId, false, AvatarFeedbackKind.UNCERTAIN)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (isCurrentTask(taskId)) {
                    showEngineError(
                        if (failure is GatewayException) failure.userMessage else "刚才的任务中断了，执行结果尚不明确，请先查看电脑端状态。",
                        taskId,
                        canRetryTask = (failure as? GatewayException)?.canRetryTask ?: false,
                        kind = failureFeedback(failure),
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
            val localDemo = state.source == AssistantSource.LOCAL_DEMO
            state.copy(
                phase = AssistantPhase.IDLE,
                currentStep = null,
                streamingReply = null,
                notice = if (localDemo) "已停止本地演示。" else "已停止接收。电脑端任务可能仍在继续。",
                isProcessing = false,
                canRetryTask = false,
                avatarFeedback = feedback(if (localDemo) AvatarFeedbackKind.CANCELLED else AvatarFeedbackKind.UNCERTAIN),
                pendingAttention = !localDemo,
            )
        }
    }

    fun onListeningStarted(): Long {
        if (mutableUiState.value.isProcessing) return 0L
        completedReturnJob?.cancel()
        val sessionId = ++nextListeningSessionId
        activeListeningSessionId = sessionId
        mutableUiState.update { state ->
            state.copy(
                phase = AssistantPhase.LISTENING,
                listeningStage = ListeningStage.PREPARING,
                listeningSessionId = sessionId,
                avatarFeedback = null,
                notice = null,
            )
        }
        return sessionId
    }

    fun onSpeechReady(sessionId: Long) {
        if (!isCurrentListeningSession(sessionId)) return
        mutableUiState.update { state ->
            if (state.listeningStage == ListeningStage.PREPARING) state.copy(listeningStage = ListeningStage.READY) else state
        }
    }

    fun onSpeechBeginning(sessionId: Long) {
        if (!isCurrentListeningSession(sessionId)) return
        mutableUiState.update { state ->
            if (state.listeningStage == ListeningStage.PREPARING || state.listeningStage == ListeningStage.READY) {
                state.copy(listeningStage = ListeningStage.SPEAKING)
            } else state
        }
    }

    fun onSpeechEnd(sessionId: Long) {
        if (!isCurrentListeningSession(sessionId)) return
        mutableUiState.update { it.copy(listeningStage = ListeningStage.RECOGNIZING) }
    }

    fun onSpeechPartialResult(text: String, sessionId: Long) {
        if (isCurrentListeningSession(sessionId) && text.isNotBlank()) {
            mutableUiState.update { it.copy(draft = text) }
        }
    }

    fun onListeningCancelled() {
        activeListeningSessionId?.let(::onListeningCancelled)
    }

    fun onListeningCancelled(sessionId: Long) {
        if (!isCurrentListeningSession(sessionId)) return
        activeListeningSessionId = null
        mutableUiState.update { state ->
            state.copy(
                phase = AssistantPhase.IDLE,
                listeningStage = ListeningStage.NONE,
                currentStep = null,
                avatarFeedback = feedback(AvatarFeedbackKind.CANCELLED),
                notice = null,
            )
        }
    }

    // 单参数入口保留给主动提交的识别文本；异步识别回调必须传捕获的 sessionId。
    fun onSpeechResult(text: String) {
        val sessionId = activeListeningSessionId ?: onListeningStarted()
        onSpeechResult(text, sessionId)
    }

    fun onSpeechResult(text: String, sessionId: Long) {
        if (!isCurrentListeningSession(sessionId)) return
        if (text.isBlank()) {
            onSpeechFailure(SpeechInputFailure.NO_MATCH, sessionId)
            return
        }
        activeListeningSessionId = null
        submitPrompt(text)
    }

    fun onSpeechFailure(failure: SpeechInputFailure) {
        val sessionId = activeListeningSessionId ?: onListeningStarted()
        onSpeechFailure(failure, sessionId)
    }

    fun onSpeechFailure(failure: SpeechInputFailure, sessionId: Long) {
        if (!isCurrentListeningSession(sessionId)) return
        activeListeningSessionId = null
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
                listeningStage = ListeningStage.NONE,
                currentStep = null,
                notice = message,
                avatarFeedback = feedback(if (failure == SpeechInputFailure.NO_MATCH) {
                    AvatarFeedbackKind.SPEECH_NOT_UNDERSTOOD
                } else AvatarFeedbackKind.FAILED),
            )
        }
    }

    private fun isCurrentListeningSession(sessionId: Long): Boolean =
        sessionId > 0L && activeListeningSessionId == sessionId &&
            mutableUiState.value.phase == AssistantPhase.LISTENING && !mutableUiState.value.isProcessing

    fun onVoicePlaybackChanged(state: VoicePlaybackState) {
        mutableUiState.update {
            it.copy(
                voicePlayback = state,
                notice = if (it.notice.isNullOrBlank() && state.playbackEvent == VoicePlaybackEvent.ERROR &&
                    state.eventSequence > it.voicePlayback.eventSequence
                ) "语音播放失败，可查看文字回复。" else it.notice,
            )
        }
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

    private fun showEngineError(message: String, taskId: Long, canRetryTask: Boolean = true, kind: AvatarFeedbackKind = AvatarFeedbackKind.FAILED) {
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
                avatarFeedback = feedback(kind),
                pendingAttention = kind == AvatarFeedbackKind.NEEDS_ATTENTION || kind == AvatarFeedbackKind.UNCERTAIN,
            )
        }
    }

    private fun feedback(kind: AvatarFeedbackKind) = AvatarFeedback(kind, ++nextFeedbackId)

    private fun deliverSpeech(sentence: String, isFirst: Boolean) {
        runCatching { speechCallback?.invoke(sentence, isFirst) }.onFailure {
            mutableUiState.update { state ->
                if (state.notice.isNullOrBlank()) state.copy(notice = "语音播放失败，可查看文字回复。") else state
            }
        }
    }

    private fun resultFeedback(result: AssistantResult): AvatarFeedbackKind {
        if (result.source == AssistantSource.LOCAL_DEMO && result.outcome == AssistantOutcome.TASK_SUCCEEDED) {
            return AvatarFeedbackKind.DEMO_SUCCEEDED
        }
        return when (result.outcome) {
            AssistantOutcome.REPLY_RECEIVED -> AvatarFeedbackKind.REPLY_READY
            AssistantOutcome.TASK_SUCCEEDED -> if (isVerifiedTerminal(result, "completed")) {
                AvatarFeedbackKind.TASK_SUCCEEDED
            } else AvatarFeedbackKind.UNCERTAIN
            AssistantOutcome.NEEDS_ATTENTION -> AvatarFeedbackKind.NEEDS_ATTENTION
            AssistantOutcome.FAILED -> AvatarFeedbackKind.FAILED
            AssistantOutcome.UNCERTAIN -> AvatarFeedbackKind.UNCERTAIN
            AssistantOutcome.CANCELLED -> if (isVerifiedTerminal(result, "cancelled")) {
                AvatarFeedbackKind.CANCELLED
            } else AvatarFeedbackKind.UNCERTAIN
        }
    }

    private fun isVerifiedTerminal(result: AssistantResult, status: String): Boolean {
        val evidence = result.evidence ?: return false
        return result.source == AssistantSource.PC_GATEWAY && !result.isSample &&
            result.requestId != null && result.requestId == currentRequestId &&
            result.taskId != null && result.taskId == mutableUiState.value.currentTaskId &&
            evidence.source == "codebuddy_run_stream" && evidence.status == status &&
            evidence.verified && evidence.terminal
    }

    private fun resultNotice(kind: AvatarFeedbackKind): String? = when (kind) {
        AvatarFeedbackKind.REPLY_READY -> null
        AvatarFeedbackKind.NEEDS_ATTENTION -> "电脑端需要确认，请先查看电脑端提示。"
        AvatarFeedbackKind.UNCERTAIN -> "尚未确认电脑任务终态，请先查看电脑端状态，避免重复执行。"
        AvatarFeedbackKind.FAILED -> "电脑任务失败，请查看回复中的原因。"
        AvatarFeedbackKind.CANCELLED -> "电脑端已确认任务取消。"
        else -> null
    }

    private fun failureFeedback(failure: Throwable): AvatarFeedbackKind {
        when ((failure as? GatewayException)?.outcome) {
            AssistantOutcome.NEEDS_ATTENTION -> return AvatarFeedbackKind.NEEDS_ATTENTION
            AssistantOutcome.UNCERTAIN, AssistantOutcome.CANCELLED -> return AvatarFeedbackKind.UNCERTAIN
            AssistantOutcome.FAILED -> return AvatarFeedbackKind.FAILED
            else -> Unit
        }
        val code = (failure as? GatewayException)?.code?.lowercase() ?: return AvatarFeedbackKind.UNCERTAIN
        return when (code) {
            "needs_attention", "busy", "workbuddy_busy", "duplicate_request", "approval_required", "awaiting_approval", "questionnaire" -> AvatarFeedbackKind.NEEDS_ATTENTION
            "submission_unknown", "ambiguous_reply", "reply_timeout", "upstream_timeout", "timeout", "stream_interrupted",
            "invalid_event", "invalid_result", "unconfirmed_response", "protocol_mismatch", "upstream_error" -> AvatarFeedbackKind.UNCERTAIN
            else -> AvatarFeedbackKind.FAILED
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
