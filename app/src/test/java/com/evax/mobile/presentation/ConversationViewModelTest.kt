package com.evax.mobile.presentation

import androidx.lifecycle.ViewModelStore
import com.evax.mobile.domain.AssistantEngine
import com.evax.mobile.domain.AssistantEvent
import com.evax.mobile.domain.AssistantOutcome
import com.evax.mobile.domain.AssistantResult
import com.evax.mobile.domain.AssistantSource
import com.evax.mobile.domain.GatewayException
import com.evax.mobile.domain.ExecutionEvidence
import com.evax.mobile.domain.SpeechInputFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = ViewModelStore()
    private lateinit var engine: RecordingAssistantEngine
    private lateinit var viewModel: ConversationViewModel

    @Before
    fun setUp() {
        engine = RecordingAssistantEngine()
        viewModel = ConversationViewModel(engine)
        store.put("conversation", viewModel)
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun submitPromptTrimsAndCompletes() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDraftChanged("  查今天的安排  ")
        viewModel.submitPrompt()
        runCurrent()

        val messages = viewModel.uiState.value.messages
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), messages.map { it.role })
        assertEquals("查今天的安排", messages.first().text)
        assertEquals("演示回复", messages.last().text)
        assertTrue(messages.last().isSample)
        assertEquals(AssistantPhase.COMPLETED, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun submitPromptIgnoresBlank() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDraftChanged("   \n ")
        viewModel.submitPrompt()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(AssistantPhase.IDLE, viewModel.uiState.value.phase)
        assertTrue(engine.prompts.isEmpty())
    }

    @Test
    fun submitPromptIgnoresWhileBusy() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flow { emit(AssistantEvent.Progress("整理中", 1, 1)); awaitCancellation() } }
        viewModel.submitPrompt("第一个任务")
        runCurrent()
        viewModel.submitPrompt("第二个任务")
        runCurrent()

        assertEquals(listOf("第一个任务"), engine.prompts)
        assertEquals(listOf("第一个任务"), viewModel.uiState.value.messages.map { it.text })
        assertTrue(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun emptySpeechResultShowsRetryNotice() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDraftChanged("保留这段文字")
        viewModel.onSpeechResult("   ")

        assertEquals("没有听清，再试一次", viewModel.uiState.value.notice)
        assertEquals("保留这段文字", viewModel.uiState.value.draft)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun permissionDeniedKeepsComposerUsable() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDraftChanged("稍后继续输入")
        viewModel.onSpeechFailure(SpeechInputFailure.PERMISSION_DENIED)

        assertTrue(viewModel.uiState.value.notice.orEmpty().contains("继续输入"))
        assertEquals("稍后继续输入", viewModel.uiState.value.draft)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun serviceUnavailableKeepsComposerUsable() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDraftChanged("手动输入")
        viewModel.onSpeechFailure(SpeechInputFailure.SERVICE_UNAVAILABLE)

        assertTrue(viewModel.uiState.value.notice.orEmpty().isNotBlank())
        assertEquals("手动输入", viewModel.uiState.value.draft)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun recognizedSpeechSubmitsMessage() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSpeechResult("整理会议纪要")
        runCurrent()

        assertEquals("整理会议纪要", engine.prompts.single())
        assertEquals("整理会议纪要", viewModel.uiState.value.messages.first().text)
        assertEquals(AssistantPhase.COMPLETED, viewModel.uiState.value.phase)
    }

    @Test
    fun listeningCallbackSetsListeningPhase() {
        viewModel.onListeningStarted()

        assertEquals(AssistantPhase.LISTENING, viewModel.uiState.value.phase)
    }

    @Test
    fun cancelledListeningReturnsToIdleWithoutErrorNotice() {
        viewModel.onListeningStarted()

        viewModel.onListeningCancelled()

        assertEquals(AssistantPhase.IDLE, viewModel.uiState.value.phase)
        assertEquals(null, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun voicePlaybackStateUpdatesWithoutChangingMessages() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.submitPrompt("查今天的安排")
        advanceUntilIdle()
        val messagesBeforePlayback = viewModel.uiState.value.messages

        viewModel.onVoicePlaybackChanged(VoicePlaybackState(isReady = true, isSpeaking = true))

        assertEquals(messagesBeforePlayback, viewModel.uiState.value.messages)
        assertEquals(VoicePlaybackState(isReady = true, isSpeaking = true), viewModel.uiState.value.voicePlayback)
    }

    @Test
    fun clearNoticeRemovesNotice() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onSpeechFailure(SpeechInputFailure.NO_MATCH)
        viewModel.clearNotice()

        assertEquals(null, viewModel.uiState.value.notice)
        assertEquals(AssistantPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun progressEventsUpdateVisibleSteps() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = {
            flowOf(
                AssistantEvent.Progress("整理会议内容", 1, 3),
                AssistantEvent.Progress("提取待办", 2, 3),
                AssistantEvent.Progress("生成摘要", 3, 3),
                AssistantEvent.Completed(AssistantResult("完成", "演示数据")),
            )
        }
        viewModel.submitPrompt("整理会议纪要")
        runCurrent()

        assertEquals(listOf("整理会议内容", "提取待办", "生成摘要"), viewModel.uiState.value.progressSteps)
        assertEquals(AssistantPhase.COMPLETED, viewModel.uiState.value.phase)
    }

    @Test
    fun engineFailureKeepsRequestAndShowsError() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flow { throw IllegalStateException("demo failure") } }
        viewModel.submitPrompt("整理会议纪要")
        advanceUntilIdle()

        assertEquals("整理会议纪要", viewModel.uiState.value.messages.single().text)
        assertEquals(AssistantPhase.ERROR, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertTrue(viewModel.uiState.value.notice.orEmpty().isNotBlank())
    }

    @Test
    fun streamingEventsTriggerSentenceSpeechAndLatency() = runTest(mainDispatcherRule.dispatcher) {
        val spokenChunks = mutableListOf<Pair<String, Boolean>>()
        val streamingVm = ConversationViewModel(
            engine = engine,
            onSpeakChunk = { sentence, isFirst -> spokenChunks += sentence to isFirst },
        )
        store.put("streaming", streamingVm)
        engine.response = {
            flowOf(
                AssistantEvent.Progress("连接 WorkBuddy 极速模型流…", 1, 2),
                AssistantEvent.StreamDelta("你好，", "你好，"),
                AssistantEvent.SpeakSentence("你好，我已经连上Mac了！", isFirst = true, latencyMs = 580L),
                AssistantEvent.StreamDelta("我已经连上Mac了！随时待命。", "你好，我已经连上Mac了！随时待命。"),
                AssistantEvent.SpeakSentence("随时待命。", isFirst = false, latencyMs = 920L),
                AssistantEvent.Completed(AssistantResult("你好，我已经连上Mac了！随时待命。", "WorkBuddy·GLM-5.3-Flash · 首句 580ms")),
            )
        }

        streamingVm.submitPrompt("你好")
        runCurrent()

        assertEquals(
            listOf("你好，我已经连上Mac了！" to true, "随时待命。" to false),
            spokenChunks,
        )
        assertEquals(580L, streamingVm.uiState.value.lastLatencyMs)
        assertEquals(AssistantPhase.COMPLETED, streamingVm.uiState.value.phase)
    }

    @Test
    fun initialStateDoesNotClaimComputerConnection() {
        assertEquals(AssistantSource.UNCONFIRMED, viewModel.uiState.value.source)
        assertEquals("未确认连接", viewModel.uiState.value.gatewayLabel)
    }

    @Test
    fun realGatewayResultIsNotMarkedAsSample() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = {
            flowOf(
                AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, "电脑网关"),
                AssistantEvent.Completed(
                    AssistantResult("真实结果", "PC Gateway", source = AssistantSource.PC_GATEWAY),
                ),
            )
        }
        viewModel.submitPrompt("测试任务")
        runCurrent()

        assertEquals(AssistantSource.PC_GATEWAY, viewModel.uiState.value.source)
        assertEquals("PC Gateway", viewModel.uiState.value.gatewayLabel)
        assertFalse(viewModel.uiState.value.messages.last().isSample)
    }

    @Test
    fun sourceFallbackClearsGatewayProgressAndLabelsLocalDemo() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = {
            flowOf(
                AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, "电脑网关"),
                AssistantEvent.Progress("电脑连接", 1, 3),
                AssistantEvent.SourceChanged(AssistantSource.LOCAL_DEMO, "本地演示"),
                AssistantEvent.Completed(
                    AssistantResult("示例结果", "演示数据", isSample = true, source = AssistantSource.LOCAL_DEMO),
                ),
            )
        }
        viewModel.submitPrompt("测试任务")
        runCurrent()

        assertEquals(AssistantSource.LOCAL_DEMO, viewModel.uiState.value.source)
        assertEquals("本地演示", viewModel.uiState.value.gatewayLabel)
        assertTrue(viewModel.uiState.value.progressSteps.isEmpty())
        assertTrue(viewModel.uiState.value.messages.last().isSample)
    }

    @Test
    fun progressKeepsCurrentStepSeparateFromCompletedSteps() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = {
            flow {
                emit(AssistantEvent.Progress("整理内容", 1, 3))
                delay(100L)
                emit(AssistantEvent.Progress("整理内容", 1, 3))
                delay(100L)
                emit(AssistantEvent.Progress("提取待办", 2, 3))
                delay(100L)
                emit(AssistantEvent.Completed(AssistantResult("完成", "演示数据", isSample = true,
                    source = AssistantSource.LOCAL_DEMO, outcome = AssistantOutcome.TASK_SUCCEEDED)))
            }
        }
        viewModel.submitPrompt("测试任务")
        runCurrent()
        assertEquals(1, viewModel.uiState.value.progressIndex)
        assertEquals(3, viewModel.uiState.value.progressTotal)
        assertTrue(viewModel.uiState.value.completedProgressSteps.isEmpty())

        advanceTimeBy(200L)
        runCurrent()
        assertEquals("提取待办", viewModel.uiState.value.currentStep)
        assertEquals(2, viewModel.uiState.value.progressIndex)
        assertEquals(listOf("整理内容"), viewModel.uiState.value.completedProgressSteps)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(listOf("整理内容", "提取待办"), viewModel.uiState.value.completedProgressSteps)
        assertEquals(3, viewModel.uiState.value.progressTotal)
    }

    @Test
    fun completionReturnsToIdleWhileKeepingReplyAndVoicePlayback() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.submitPrompt("测试任务")
        runCurrent()
        assertEquals(AssistantPhase.COMPLETED, viewModel.uiState.value.phase)
        val messages = viewModel.uiState.value.messages
        viewModel.onVoicePlaybackChanged(VoicePlaybackState(isReady = true, isSpeaking = true))

        advanceTimeBy(1_999L)
        runCurrent()
        assertEquals(AssistantPhase.COMPLETED, viewModel.uiState.value.phase)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(AssistantPhase.IDLE, viewModel.uiState.value.phase)
        assertEquals(messages, viewModel.uiState.value.messages)
        assertTrue(viewModel.uiState.value.voicePlayback.isSpeaking)
    }

    @Test
    fun oldCompletionTimerDoesNotOverrideListening() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.submitPrompt("测试任务")
        runCurrent()
        viewModel.onListeningStarted()
        advanceTimeBy(2_500L)
        runCurrent()

        assertEquals(AssistantPhase.LISTENING, viewModel.uiState.value.phase)
    }

    @Test
    fun oldCompletionTimerDoesNotOverrideNewTaskOrError() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.submitPrompt("第一项任务")
        runCurrent()
        engine.response = { flow { delay(3_000L); throw IllegalStateException("gateway failed") } }
        viewModel.submitPrompt("第二项任务")
        runCurrent()
        advanceTimeBy(2_500L)
        runCurrent()
        assertEquals(AssistantPhase.THINKING, viewModel.uiState.value.phase)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(AssistantPhase.ERROR, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.notice.orEmpty().contains("演示"))
    }

    @Test
    fun cancelProcessingCancelsCoroutineAndKeepsConversation() = runTest(mainDispatcherRule.dispatcher) {
        var cancelled = false
        engine.response = {
            flow {
                emit(AssistantEvent.Progress("正在处理", 1, 2))
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        viewModel.submitPrompt("保留我的请求")
        runCurrent()
        viewModel.cancelProcessing()
        runCurrent()

        assertTrue(cancelled)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertEquals(AssistantPhase.IDLE, viewModel.uiState.value.phase)
        assertEquals("已停止接收。电脑端任务可能仍在继续。", viewModel.uiState.value.notice)
        assertEquals(listOf("保留我的请求"), viewModel.uiState.value.messages.map { it.text })
        assertTrue(viewModel.uiState.value.completedProgressSteps.isEmpty())
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun lateCancelledEventsCannotChangeOrSpeakOverNewTask() = runTest(mainDispatcherRule.dispatcher) {
        val spoken = mutableListOf<String>()
        val lateEngine = object : AssistantEngine {
            override fun respond(prompt: String): Flow<AssistantEvent> = object : Flow<AssistantEvent> {
                override suspend fun collect(collector: FlowCollector<AssistantEvent>) {
                    if (prompt == "旧任务") {
                        try {
                            awaitCancellation()
                        } catch (_: CancellationException) {
                            // 模拟不能立刻停止的外部回调，故意在取消后送达。
                            withContext(NonCancellable) {
                                delay(500L)
                                collector.emit(AssistantEvent.SourceChanged(AssistantSource.LOCAL_DEMO, "过期来源"))
                                collector.emit(AssistantEvent.Progress("过期步骤", 1, 1))
                                collector.emit(AssistantEvent.SpeakSentence("过期语音", true))
                                collector.emit(AssistantEvent.Completed(AssistantResult("过期结果", "演示数据")))
                            }
                        }
                    } else {
                        collector.emit(AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, "电脑网关"))
                        delay(1_000L)
                        collector.emit(AssistantEvent.Completed(AssistantResult("新结果", "PC Gateway")))
                    }
                }
            }
        }
        val guardedVm = ConversationViewModel(lateEngine, onSpeakChunk = { text, _ -> spoken += text })
        store.put("guarded", guardedVm)
        guardedVm.submitPrompt("旧任务")
        runCurrent()
        guardedVm.cancelProcessing()
        guardedVm.submitPrompt("新任务")
        runCurrent()
        advanceTimeBy(600L)
        runCurrent()

        assertTrue(guardedVm.uiState.value.isProcessing)
        assertEquals(AssistantSource.PC_GATEWAY, guardedVm.uiState.value.source)
        assertTrue(guardedVm.uiState.value.progressSteps.isEmpty())
        assertEquals(listOf("旧任务", "新任务"), guardedVm.uiState.value.messages.map { it.text })
        assertTrue(spoken.isEmpty())

        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf("旧任务", "新任务", "新结果"), guardedVm.uiState.value.messages.map { it.text })
        assertEquals(listOf("新结果"), spoken)
    }

    @Test
    fun speechCallbackCanBeReboundAndDetachedAfterActivityRecreation() = runTest(mainDispatcherRule.dispatcher) {
        val oldActivitySpeech = mutableListOf<String>()
        val newActivitySpeech = mutableListOf<String>()
        val reboundVm = ConversationViewModel(engine, onSpeakChunk = { text, _ -> oldActivitySpeech += text })
        store.put("rebound", reboundVm)
        reboundVm.submitPrompt("第一项任务")
        runCurrent()
        reboundVm.setSpeechCallback { text, _ -> newActivitySpeech += text }
        reboundVm.submitPrompt("第二项任务")
        runCurrent()
        reboundVm.setSpeechCallback(null)
        reboundVm.submitPrompt("第三项任务")
        runCurrent()

        assertEquals(listOf("演示回复"), oldActivitySpeech)
        assertEquals(listOf("演示回复"), newActivitySpeech)
        assertEquals(6, reboundVm.uiState.value.messages.size)
    }

    @Test
    fun dismissingEngineErrorReturnsToIdleAndKeepsHistory() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flow { throw IllegalStateException("gateway failed") } }
        viewModel.submitPrompt("保留我的请求")
        runCurrent()
        assertEquals(AssistantPhase.ERROR, viewModel.uiState.value.phase)

        viewModel.clearNotice()

        assertEquals(AssistantPhase.IDLE, viewModel.uiState.value.phase)
        assertEquals(null, viewModel.uiState.value.notice)
        assertEquals(listOf("保留我的请求"), viewModel.uiState.value.messages.map { it.text })
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun gatewayFailurePreservesActionableMessageWithoutFabricatingReply() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flow { throw GatewayException("AUTH_REQUIRED", "请先在电脑端完成 WorkBuddy 授权。") } }
        viewModel.submitPrompt("处理电脑上的文档")
        runCurrent()

        assertEquals(AssistantPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals("请先在电脑端完成 WorkBuddy 授权。", viewModel.uiState.value.notice)
        assertEquals(listOf(MessageRole.USER), viewModel.uiState.value.messages.map { it.role })
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun uncertainOrBusyGatewayResultDisablesOneTapRetry() = runTest(mainDispatcherRule.dispatcher) {
        for (code in listOf(
            "SUBMISSION_UNKNOWN", "NEEDS_ATTENTION", "AMBIGUOUS_REPLY", "REPLY_TIMEOUT", "stream_interrupted", "BUSY", "DUPLICATE_REQUEST",
            "invalid_event", "invalid_result", "protocol_mismatch", "unconfirmed_response", "UPSTREAM_ERROR",
        )) {
            engine.response = { flow { throw GatewayException(code, "请先查看电脑端状态。") } }
            viewModel.submitPrompt("处理文档")
            runCurrent()
            assertEquals(AssistantPhase.ERROR, viewModel.uiState.value.phase)
            assertFalse(viewModel.uiState.value.canRetryTask)
            assertFalse(viewModel.uiState.value.isProcessing)
        }
    }

    @Test
    fun listeningStagesFollowActualCallbacksWithoutRegressingOnLateReady() {
        val session = viewModel.onListeningStarted()
        assertEquals(ListeningStage.PREPARING, viewModel.uiState.value.listeningStage)
        viewModel.onSpeechReady(session)
        assertEquals(ListeningStage.READY, viewModel.uiState.value.listeningStage)
        viewModel.onSpeechBeginning(session)
        assertEquals(ListeningStage.SPEAKING, viewModel.uiState.value.listeningStage)
        viewModel.onSpeechReady(session)
        assertEquals(ListeningStage.SPEAKING, viewModel.uiState.value.listeningStage)
        viewModel.onSpeechEnd(session)
        viewModel.onSpeechBeginning(session)
        assertEquals(ListeningStage.RECOGNIZING, viewModel.uiState.value.listeningStage)
    }

    @Test
    fun cancelledSpeechCallbacksCannotEditDraftOrSubmitOverNewSession() = runTest(mainDispatcherRule.dispatcher) {
        val old = viewModel.onListeningStarted()
        viewModel.onListeningCancelled(old)
        val current = viewModel.onListeningStarted()
        viewModel.onSpeechPartialResult("当前文字", current)
        viewModel.onSpeechReady(old)
        viewModel.onSpeechPartialResult("旧文字", old)
        viewModel.onSpeechResult("旧指令", old)
        viewModel.onSpeechFailure(SpeechInputFailure.PERMISSION_DENIED, old)
        viewModel.onListeningCancelled(old)
        runCurrent()
        assertEquals(ListeningStage.PREPARING, viewModel.uiState.value.listeningStage)
        assertEquals("当前文字", viewModel.uiState.value.draft)
        assertTrue(engine.prompts.isEmpty())
        assertEquals(null, viewModel.uiState.value.notice)
    }

    @Test
    fun recognizedFinalInvalidatesDuplicateResultAndPartialCallbacks() = runTest(mainDispatcherRule.dispatcher) {
        val session = viewModel.onListeningStarted()
        viewModel.onSpeechEnd(session)
        viewModel.onSpeechResult("整理会议纪要", session)
        runCurrent()
        val state = viewModel.uiState.value
        viewModel.onSpeechResult("重复指令", session)
        viewModel.onSpeechPartialResult("迟到文字", session)
        viewModel.onSpeechFailure(SpeechInputFailure.NO_MATCH, session)
        assertEquals(listOf("整理会议纪要"), engine.prompts)
        assertEquals(state, viewModel.uiState.value)
        assertEquals(ListeningStage.NONE, state.listeningStage)
    }

    @Test
    fun noMatchAndPermissionFailureHaveDifferentFeedback() {
        val first = viewModel.onListeningStarted()
        viewModel.onSpeechFailure(SpeechInputFailure.NO_MATCH, first)
        assertEquals(AvatarFeedbackKind.SPEECH_NOT_UNDERSTOOD, viewModel.uiState.value.avatarFeedback?.kind)
        assertEquals(ListeningStage.NONE, viewModel.uiState.value.listeningStage)
        val second = viewModel.onListeningStarted()
        viewModel.onSpeechFailure(SpeechInputFailure.PERMISSION_DENIED, second)
        assertEquals(AvatarFeedbackKind.FAILED, viewModel.uiState.value.avatarFeedback?.kind)
        assertTrue(viewModel.uiState.value.notice.orEmpty().contains("麦克风权限"))
    }

    @Test
    fun plainComputerReplyDoesNotClaimTaskSuccessOrCompleteCurrentStep() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flowOf(
            AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, "电脑网关"),
            AssistantEvent.Progress("已提交电脑", 1, 2),
            AssistantEvent.Completed(AssistantResult("我已经完成了任务", "官方回复", source = AssistantSource.PC_GATEWAY)),
        ) }
        viewModel.submitPrompt("处理文档")
        runCurrent()
        assertEquals(AvatarFeedbackKind.REPLY_READY, viewModel.uiState.value.avatarFeedback?.kind)
        assertTrue(viewModel.uiState.value.completedProgressSteps.isEmpty())
        assertFalse(viewModel.uiState.value.messages.last().isSample)
    }

    @Test
    fun taskSuccessRequiresMatchingBoundIdsAndVerifiedTerminalEvidence() = runTest(mainDispatcherRule.dispatcher) {
        val valid = AssistantResult("任务已确认完成", "官方回复", source = AssistantSource.PC_GATEWAY,
            outcome = AssistantOutcome.TASK_SUCCEEDED, requestId = "request-1", taskId = "run-1",
            evidence = ExecutionEvidence("codebuddy_run_stream", "completed", verified = true, terminal = true))
        for (result in listOf(valid, valid.copy(evidence = null), valid.copy(evidence = valid.evidence?.copy(terminal = false)),
            valid.copy(requestId = null), valid.copy(taskId = null), valid.copy(isSample = true),
            valid.copy(evidence = valid.evidence?.copy(source = "workbuddy_message")))) {
            engine.response = { flowOf(
                AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, "电脑网关"),
                AssistantEvent.RequestStarted("request-1"),
                AssistantEvent.Progress("电脑已接收", 1, 2, "run-1", "request-1"),
                AssistantEvent.Completed(result),
            ) }
            viewModel.submitPrompt("处理文档")
            runCurrent()
            assertEquals(if (result == valid) AvatarFeedbackKind.TASK_SUCCEEDED else AvatarFeedbackKind.UNCERTAIN,
                viewModel.uiState.value.avatarFeedback?.kind)
        }
    }

    @Test
    fun localDemoCannotDropSampleFlagOrClaimComputerTaskSuccess() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flowOf(AssistantEvent.Completed(AssistantResult("本地样例", "错误的官方标签",
            isSample = false, source = AssistantSource.LOCAL_DEMO, outcome = AssistantOutcome.TASK_SUCCEEDED))) }
        viewModel.submitPrompt("演示")
        runCurrent()
        assertEquals(AvatarFeedbackKind.DEMO_SUCCEEDED, viewModel.uiState.value.avatarFeedback?.kind)
        assertTrue(viewModel.uiState.value.messages.last().isSample)
        assertEquals("演示数据", viewModel.uiState.value.messages.last().sampleLabel)
    }

    @Test
    fun stoppingLocalDemoDoesNotClaimUncertainComputerOperation() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flow {
            emit(AssistantEvent.SourceChanged(AssistantSource.LOCAL_DEMO, "本地演示"))
            awaitCancellation()
        } }
        viewModel.submitPrompt("演示")
        runCurrent()
        viewModel.cancelProcessing()
        assertEquals(AvatarFeedbackKind.CANCELLED, viewModel.uiState.value.avatarFeedback?.kind)
        assertFalse(viewModel.uiState.value.pendingAttention)
        assertEquals("已停止本地演示。", viewModel.uiState.value.notice)
    }

    @Test
    fun mismatchedTaskCannotAppendForeignReplyOrSpeak() = runTest(mainDispatcherRule.dispatcher) {
        val spoken = mutableListOf<String>()
        viewModel.setSpeechCallback { text, _ -> spoken += text }
        engine.response = { flowOf(
            AssistantEvent.RequestStarted("current-request"),
            AssistantEvent.Progress("电脑已接收", 1, 2, "current-run", "current-request"),
            AssistantEvent.Completed(AssistantResult("别的任务结果", "官方回复", source = AssistantSource.PC_GATEWAY,
                outcome = AssistantOutcome.TASK_SUCCEEDED, requestId = "current-request", taskId = "old-run",
                evidence = ExecutionEvidence("codebuddy_run_stream", "completed", true, true))),
        ) }
        viewModel.submitPrompt("当前任务")
        runCurrent()
        assertEquals(listOf("当前任务"), viewModel.uiState.value.messages.map { it.text })
        assertEquals(AvatarFeedbackKind.UNCERTAIN, viewModel.uiState.value.avatarFeedback?.kind)
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun agySuccessRequiresTheSameBoundTaskAndStructuredCompletedEvidence() = runTest(mainDispatcherRule.dispatcher) {
        val valid = AssistantResult("本机 Agent 终态已确认", "本机 Agent", source = AssistantSource.PC_GATEWAY,
            outcome = AssistantOutcome.TASK_SUCCEEDED, requestId = "agy-request", taskId = "agy-conversation",
            evidence = ExecutionEvidence("agy_cli_agent", "completed", verified = true, terminal = true))
        for ((result, bindTask) in listOf(
            valid to true,
            valid.copy(evidence = valid.evidence?.copy(status = "SUCCESS")) to true,
            valid.copy(evidence = null) to true,
            valid to false,
            valid.copy(taskId = "other-conversation") to true,
        )) {
            engine.response = { flow {
                emit(AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, "电脑桥接"))
                emit(AssistantEvent.RequestStarted("agy-request"))
                if (bindTask) emit(AssistantEvent.Progress("Agent 已启动", 1, 3, "agy-conversation", "agy-request"))
                emit(AssistantEvent.Completed(result))
            } }
            viewModel.submitPrompt("电脑任务")
            runCurrent()
            assertEquals(if (result == valid && bindTask) AvatarFeedbackKind.TASK_SUCCEEDED else AvatarFeedbackKind.UNCERTAIN,
                viewModel.uiState.value.avatarFeedback?.kind)
        }
    }

    @Test
    fun duplicateCompletionCannotAppendSpeakOrTriggerFeedbackAgain() = runTest(mainDispatcherRule.dispatcher) {
        val spoken = mutableListOf<String>()
        viewModel.setSpeechCallback { text, _ -> spoken += text }
        val event = AssistantEvent.Completed(AssistantResult("一份回复", "官方回复", source = AssistantSource.PC_GATEWAY))
        engine.response = { flowOf(event, event, AssistantEvent.SpeakSentence("迟到句子", false)) }
        viewModel.submitPrompt("当前任务")
        runCurrent()
        assertEquals(listOf("当前任务", "一份回复"), viewModel.uiState.value.messages.map { it.text })
        assertEquals(listOf("一份回复"), spoken)
        assertEquals(1L, viewModel.uiState.value.avatarFeedback?.eventId)
    }

    @Test
    fun pendingFailedUncertainAndLocalStopHaveDistinctFeedback() = runTest(mainDispatcherRule.dispatcher) {
        for ((outcome, kind) in listOf(
            AssistantOutcome.NEEDS_ATTENTION to AvatarFeedbackKind.NEEDS_ATTENTION,
            AssistantOutcome.FAILED to AvatarFeedbackKind.FAILED,
            AssistantOutcome.UNCERTAIN to AvatarFeedbackKind.UNCERTAIN,
        )) {
            engine.response = { flowOf(AssistantEvent.Completed(AssistantResult("状态说明", "官方回复",
                source = AssistantSource.PC_GATEWAY, outcome = outcome))) }
            viewModel.submitPrompt("检查")
            runCurrent()
            assertEquals(kind, viewModel.uiState.value.avatarFeedback?.kind)
            assertEquals(outcome != AssistantOutcome.FAILED, viewModel.uiState.value.pendingAttention)
            assertEquals("状态说明", viewModel.uiState.value.messages.last().text)
        }
        engine.response = { flow { awaitCancellation() } }
        viewModel.submitPrompt("电脑任务")
        runCurrent()
        viewModel.cancelProcessing()
        assertEquals(AvatarFeedbackKind.UNCERTAIN, viewModel.uiState.value.avatarFeedback?.kind)
        assertTrue(viewModel.uiState.value.pendingAttention)
    }

    @Test
    fun ttsDoneAndErrorCannotChangeReplyIntoTaskSuccess() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flowOf(AssistantEvent.Completed(AssistantResult("普通回复", "官方回复", source = AssistantSource.PC_GATEWAY))) }
        viewModel.submitPrompt("检查")
        runCurrent()
        val feedback = viewModel.uiState.value.avatarFeedback
        viewModel.onVoicePlaybackChanged(VoicePlaybackState(playbackEvent = VoicePlaybackEvent.DONE, eventSequence = 1))
        assertEquals(feedback, viewModel.uiState.value.avatarFeedback)
        viewModel.onVoicePlaybackChanged(VoicePlaybackState(playbackEvent = VoicePlaybackEvent.ERROR, eventSequence = 2, errorCode = -1))
        assertEquals(feedback, viewModel.uiState.value.avatarFeedback)
        assertTrue(viewModel.uiState.value.notice.orEmpty().contains("语音播放失败"))
    }

    @Test
    fun dismissingPendingTaskUncertainResultOrAttentionErrorPreservesPendingAndRetryGuard() = runTest(mainDispatcherRule.dispatcher) {
        for (response in listOf<(String) -> Flow<AssistantEvent>>(
            { flowOf(AssistantEvent.Completed(AssistantResult("电脑回复待确认", "官方回复",
                source = AssistantSource.PC_GATEWAY, outcome = AssistantOutcome.NEEDS_ATTENTION))) },
            { flowOf(AssistantEvent.Completed(AssistantResult("电脑终态未知", "官方回复",
                source = AssistantSource.PC_GATEWAY, outcome = AssistantOutcome.UNCERTAIN))) },
            { flow { throw GatewayException("NEEDS_ATTENTION", "先确认电脑状态") } },
        )) {
            engine.response = response
            viewModel.submitPrompt("检查")
            runCurrent()
            assertTrue(viewModel.uiState.value.pendingAttention)
            assertFalse(viewModel.uiState.value.canRetryTask)
            val feedback = viewModel.uiState.value.avatarFeedback
            viewModel.clearNotice()
            assertEquals(null, viewModel.uiState.value.notice)
            assertTrue(viewModel.uiState.value.pendingAttention)
            assertFalse(viewModel.uiState.value.canRetryTask)
            assertEquals(feedback, viewModel.uiState.value.avatarFeedback)
        }
    }

    @Test
    fun dismissingLocalStopOfComputerReceptionDoesNotConfirmRemoteCancellation() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flow { awaitCancellation() } }
        viewModel.submitPrompt("电脑任务")
        runCurrent()
        viewModel.cancelProcessing()
        viewModel.clearNotice()
        assertTrue(viewModel.uiState.value.pendingAttention)
        assertFalse(viewModel.uiState.value.canRetryTask)
        assertEquals(AvatarFeedbackKind.UNCERTAIN, viewModel.uiState.value.avatarFeedback?.kind)
    }

    @Test
    fun dismissingKnownPreflightFailureReturnsToIdleWithoutPendingAttention() = runTest(mainDispatcherRule.dispatcher) {
        engine.response = { flow { throw GatewayException("offline", "电脑离线，未提交任务") } }
        viewModel.submitPrompt("检查")
        runCurrent()
        assertEquals(AvatarFeedbackKind.FAILED, viewModel.uiState.value.avatarFeedback?.kind)
        assertFalse(viewModel.uiState.value.pendingAttention)
        val retryBefore = viewModel.uiState.value.canRetryTask
        viewModel.clearNotice()
        assertEquals(AssistantPhase.IDLE, viewModel.uiState.value.phase)
        assertEquals(null, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.pendingAttention)
        assertEquals(retryBefore, viewModel.uiState.value.canRetryTask)
    }

    @Test
    fun agyFailureStillNeedsComputerInspectionAndCannotEnableRetryOnDismiss() = runTest(mainDispatcherRule.dispatcher) {
        val evidence = ExecutionEvidence("agy_cli_agent", "failed", true, true)
        for (response in listOf<(String) -> Flow<AssistantEvent>>(
            { flowOf(AssistantEvent.Completed(AssistantResult("Agent 任务失败", "本机 Agent",
                source = AssistantSource.PC_GATEWAY, outcome = AssistantOutcome.FAILED, evidence = evidence))) },
            { flow { throw GatewayException("TASK_FAILED", "Agent 任务失败", outcome = AssistantOutcome.FAILED, evidence = evidence) } },
        )) {
            engine.response = response
            viewModel.submitPrompt("电脑任务")
            runCurrent()
            assertEquals(AvatarFeedbackKind.FAILED, viewModel.uiState.value.avatarFeedback?.kind)
            assertTrue(viewModel.uiState.value.pendingAttention)
            assertFalse(viewModel.uiState.value.canRetryTask)
            viewModel.clearNotice()
            assertTrue(viewModel.uiState.value.pendingAttention)
            assertFalse(viewModel.uiState.value.canRetryTask)
        }
    }
}

private class RecordingAssistantEngine : AssistantEngine {
    val prompts = mutableListOf<String>()
    var response: (String) -> Flow<AssistantEvent> = {
        flowOf(AssistantEvent.Completed(AssistantResult("演示回复", "演示数据", isSample = true,
            source = AssistantSource.LOCAL_DEMO, outcome = AssistantOutcome.TASK_SUCCEEDED)))
    }

    override fun respond(prompt: String): Flow<AssistantEvent> {
        prompts += prompt
        return response(prompt)
    }
}
