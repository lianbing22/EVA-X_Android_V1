package com.evax.mobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.evax.mobile.domain.AssistantSource
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.ConversationMessage
import com.evax.mobile.presentation.ConversationUiState
import com.evax.mobile.presentation.MessageRole
import com.evax.mobile.presentation.VoicePlaybackState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConversationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultHomeKeepsSettingsConversationAndToolsInDrawers() {
        composeRule.setConversationScreen()

        composeRule.onNodeWithContentDescription("EVA 双眼，准备就绪").assertIsDisplayed()
        composeRule.onNodeWithText("语音输入").assertIsDisplayed()
        composeRule.onNodeWithText("工具").assertIsDisplayed()
        composeRule.onNodeWithText("查今天的安排").assertDoesNotExist()
        composeRule.onNodeWithText("播报语速").assertDoesNotExist()
        composeRule.onNodeWithText("输入你的指令…").assertDoesNotExist()
        composeRule.onNodeWithText("电脑连接尚未确认").assertDoesNotExist()
    }

    @Test
    fun exampleChipsSubmitExpectedPrompts() {
        val submitted = mutableListOf<String>()
        composeRule.setConversationScreen(onSubmit = { submitted += it })

        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("查今天的安排").performClick()
        composeRule.onNodeWithText("快捷工具").assertDoesNotExist()
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("整理会议纪要").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("查今天的安排", "整理会议纪要"), submitted)
        }
    }

    @Test
    fun screenShowsDemoModeAndSampleLabels() {
        composeRule.setConversationScreen(
            state = ConversationUiState(
                phase = AssistantPhase.COMPLETED,
                source = AssistantSource.LOCAL_DEMO,
                messages = listOf(
                    ConversationMessage(
                        id = 1,
                        role = MessageRole.ASSISTANT,
                        text = "下午 3 点有客户需求讨论，5 点有项目复盘。",
                        isSample = true,
                    ),
                ),
            ),
        )

        composeRule.openConversationDetails()
        composeRule.onNodeWithText("本地演示 · 示例内容").assertIsDisplayed()
        composeRule.onAllNodesWithText("演示数据").onLast().assertIsDisplayed()
        composeRule.onAllNodesWithText("下午 3 点有客户需求讨论，5 点有项目复盘。").onLast().assertIsDisplayed()
    }

    @Test
    fun avatarShowsAccessibleStateLabel() {
        val state = mutableStateOf(ConversationUiState(phase = AssistantPhase.LISTENING))
        composeRule.setContent {
            ConversationScreen(
                state = state.value,
                onDraftChanged = {},
                onSubmit = {},
                onMicTap = {},
                onSpeakLatest = {},
                onStopSpeaking = {},
            )
        }
        composeRule.onNodeWithText("正在聆听").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("EVA 双眼，正在聆听").assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = ConversationUiState(
                phase = AssistantPhase.EXECUTING,
                isProcessing = true,
                currentStep = "提取待办",
                progressIndex = 2,
                progressTotal = 3,
                progressSteps = listOf("整理会议内容", "提取待办"),
                completedProgressSteps = listOf("整理会议内容"),
            )
        }
        composeRule.onNodeWithText("正在执行").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("EVA 双眼，正在执行").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        composeRule.openConversationDetails()
        composeRule.onNodeWithText("执行中").assertIsDisplayed()
    }

    @Test
    fun playbackControlsReflectReadyAndSpeakingState() {
        val state = mutableStateOf(
            ConversationUiState(
                phase = AssistantPhase.COMPLETED,
                messages = listOf(
                    ConversationMessage(1, MessageRole.ASSISTANT, "演示回答", isSample = true),
                ),
            ),
        )
        var playRequests = 0
        var stopRequests = 0
        composeRule.setContent {
            ConversationScreen(
                state = state.value,
                onDraftChanged = {},
                onSubmit = {},
                onMicTap = {},
                onSpeakLatest = { playRequests++ },
                onStopSpeaking = { stopRequests++ },
            )
        }
        composeRule.onNodeWithText("朗读回复").assertDoesNotExist()
        composeRule.openConversationDetails()
        composeRule.onNodeWithText("朗读回复").assertIsNotEnabled()
        composeRule.onAllNodesWithText("演示回答").onLast().assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = state.value.copy(voicePlayback = VoicePlaybackState(isReady = true))
        }
        composeRule.onNodeWithText("朗读回复").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                voicePlayback = VoicePlaybackState(isReady = true, isSpeaking = true),
            )
        }
        composeRule.onNodeWithText("停止播报").assertIsEnabled().performClick()
        composeRule.onAllNodesWithText("演示回答").onLast().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, playRequests)
            assertEquals(1, stopRequests)
        }
    }

    @Test
    fun toolAndConversationDrawersCanOpenAndClose() {
        composeRule.setConversationScreen()
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("快捷工具").assertIsDisplayed()
        composeRule.onNodeWithText("电脑连接尚未确认").assertIsDisplayed()
        composeRule.onNodeWithText("对话详情").performClick()
        composeRule.onNodeWithText("输入你的指令…").assertIsDisplayed()
        composeRule.onNodeWithText("发送").assertIsNotEnabled()
        composeRule.onNodeWithText("收起").performClick()
        composeRule.onNodeWithText("输入你的指令…").assertDoesNotExist()
        composeRule.onNodeWithText("工具").assertIsDisplayed()
    }

    @Test
    fun realGatewaySourceDoesNotShowSampleBadge() {
        composeRule.setConversationScreen(
            state = ConversationUiState(
                phase = AssistantPhase.COMPLETED,
                source = AssistantSource.PC_GATEWAY,
                messages = listOf(ConversationMessage(1, MessageRole.ASSISTANT, "电脑任务真实回复")),
            ),
        )
        composeRule.openConversationDetails()
        composeRule.onNodeWithText("WorkBuddy · 电脑任务通道").assertIsDisplayed()
        composeRule.onNodeWithText("本地演示 · 示例内容").assertDoesNotExist()
        composeRule.onNodeWithText("演示数据").assertDoesNotExist()
        composeRule.onAllNodesWithText("电脑任务真实回复").onLast().assertIsDisplayed()
    }

    @Test
    fun changingCompanionModesCallsHostAndRestCanWake() {
        val modes = mutableListOf<EvaCompanionMode>()
        composeRule.setConversationScreen(onModeChanged = { modes += it })
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("勿扰").performScrollTo().performClick()
        composeRule.onNodeWithText("收起").performClick()
        composeRule.onNodeWithContentDescription("EVA 双眼，勿扰").assertIsDisplayed()
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("静息").performScrollTo().performClick()
        composeRule.onNodeWithText("唤醒").assertIsDisplayed()
        composeRule.onNodeWithText("工具").assertDoesNotExist()
        composeRule.onNodeWithText("唤醒").performClick()
        composeRule.onNodeWithContentDescription("EVA 双眼，准备就绪").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                listOf(EvaCompanionMode.COMPANION, EvaCompanionMode.DND, EvaCompanionMode.REST, EvaCompanionMode.COMPANION),
                modes,
            )
        }
    }

    @Test
    fun taskCancelInvokesHostAndBusyToolsCannotSubmit() {
        val state = mutableStateOf(
            ConversationUiState(
                phase = AssistantPhase.EXECUTING,
                isProcessing = true,
                currentStep = "提取待办",
                progressIndex = 2,
                progressTotal = 3,
            ),
        )
        var cancelled = 0
        composeRule.setContent {
            ConversationScreen(
                state = state.value,
                onDraftChanged = {},
                onSubmit = {},
                onMicTap = {},
                onSpeakLatest = {},
                onStopSpeaking = {},
                onCancelTask = {
                    cancelled++
                    state.value = state.value.copy(
                        phase = AssistantPhase.IDLE,
                        isProcessing = false,
                        currentStep = null,
                        notice = "已停止接收。电脑端任务可能仍在继续。",
                    )
                },
            )
        }
        composeRule.onNodeWithText("语音输入").assertIsNotEnabled()
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("查今天的安排").assertIsNotEnabled()
        composeRule.onNodeWithText("收起").performClick()
        composeRule.onNodeWithText("停止接收").performClick()
        composeRule.onNodeWithText("已停止接收。电脑端任务可能仍在继续。").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test
    fun stopListeningInvokesHostWithoutOpeningNewVoiceDisclosure() {
        var stops = 0
        composeRule.setContent {
            ConversationScreen(
                state = ConversationUiState(phase = AssistantPhase.LISTENING),
                onDraftChanged = {},
                onSubmit = {},
                onMicTap = {},
                onSpeakLatest = {},
                onStopSpeaking = {},
                onStopListening = { stops++ },
            )
        }
        composeRule.onNodeWithText("停止聆听").performClick()
        composeRule.onNodeWithText("开始语音输入").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, stops) }
    }
}

private fun ComposeContentTestRule.openConversationDetails() {
    onNodeWithText("工具").performClick()
    onNodeWithText("对话详情").performClick()
}

private fun ComposeContentTestRule.setConversationScreen(
    state: ConversationUiState = ConversationUiState(),
    onSubmit: (String) -> Unit = {},
    onModeChanged: (EvaCompanionMode) -> Unit = {},
) {
    setContent {
        ConversationScreen(
            state = state,
            onDraftChanged = {},
            onSubmit = onSubmit,
            onMicTap = {},
            onSpeakLatest = {},
            onStopSpeaking = {},
            onCompanionModeChanged = onModeChanged,
        )
    }
}
