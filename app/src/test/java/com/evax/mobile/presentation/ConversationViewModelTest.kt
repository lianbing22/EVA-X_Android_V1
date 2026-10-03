package com.evax.mobile.presentation

import androidx.lifecycle.ViewModelStore
import com.evax.mobile.domain.AssistantEngine
import com.evax.mobile.domain.AssistantEvent
import com.evax.mobile.domain.AssistantResult
import com.evax.mobile.domain.SpeechInputFailure
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        assertEquals(
            listOf("你好，我已经连上Mac了！" to true, "随时待命。" to false),
            spokenChunks,
        )
        assertEquals(580L, streamingVm.uiState.value.lastLatencyMs)
        assertEquals(AssistantPhase.COMPLETED, streamingVm.uiState.value.phase)
    }
}

private class RecordingAssistantEngine : AssistantEngine {
    val prompts = mutableListOf<String>()
    var response: (String) -> Flow<AssistantEvent> = {
        flowOf(AssistantEvent.Completed(AssistantResult("演示回复", "演示数据")))
    }

    override fun respond(prompt: String): Flow<AssistantEvent> {
        prompts += prompt
        return response(prompt)
    }
}
