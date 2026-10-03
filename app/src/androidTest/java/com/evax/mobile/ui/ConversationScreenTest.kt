package com.evax.mobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.ComposeContentTestRule
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
    fun exampleChipsSubmitExpectedPrompts() {
        val submitted = mutableListOf<String>()
        composeRule.setConversationScreen(onSubmit = { submitted += it })

        composeRule.onNodeWithText("查今天的安排").performClick()
        composeRule.onNodeWithText("整理会议纪要").performClick()

        assertEquals(listOf("查今天的安排", "整理会议纪要"), submitted)
    }

    @Test
    fun screenShowsDemoModeAndSampleLabels() {
        composeRule.setContent {
            ConversationScreen(
                state = ConversationUiState(
                    phase = AssistantPhase.COMPLETED,
                    messages = listOf(
                        ConversationMessage(
                            id = 1,
                            role = MessageRole.ASSISTANT,
                            text = "下午 3 点有客户需求讨论，5 点有项目复盘。",
                            isSample = true,
                        ),
                    ),
                ),
                onDraftChanged = {},
                onSubmit = {},
                onMicTap = {},
                onSpeakLatest = {},
                onStopSpeaking = {},
            )
        }

        composeRule.onNodeWithText("演示模式").assertIsDisplayed()
        composeRule.onNodeWithText("演示数据").assertIsDisplayed()
        composeRule.onNodeWithText("下午 3 点有客户需求讨论，5 点有项目复盘。").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("正在聆听").assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = ConversationUiState(
                phase = AssistantPhase.EXECUTING,
                currentStep = "提取待办",
            )
        }
        composeRule.onNodeWithText("正在执行").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("正在执行").assertIsDisplayed()
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

        composeRule.onNodeWithText("朗读回复").assertIsNotEnabled()
        composeRule.onNodeWithText("演示回答").assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = state.value.copy(voicePlayback = VoicePlaybackState(isReady = true))
        }
        composeRule.onNodeWithText("朗读回复").assertIsEnabled()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                voicePlayback = VoicePlaybackState(isReady = true, isSpeaking = true),
            )
        }
        composeRule.onNodeWithText("停止播报").assertIsEnabled()
        composeRule.onNodeWithText("演示回答").assertIsDisplayed()
    }
}

private fun ComposeContentTestRule.setConversationScreen(
    onSubmit: (String) -> Unit,
) {
    setContent {
        ConversationScreen(
            state = ConversationUiState(),
            onDraftChanged = {},
            onSubmit = onSubmit,
            onMicTap = {},
            onSpeakLatest = {},
            onStopSpeaking = {},
        )
    }
}
