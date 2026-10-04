package com.evax.mobile.platform.voice

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.evax.mobile.presentation.ConversationUiState
import com.evax.mobile.ui.ConversationScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpeechPermissionDisclosureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun micTapShowsSpeechServiceDisclosureBeforePermissionRequest() {
        var permissionRequests = 0
        composeRule.setContent {
            ConversationScreen(
                state = ConversationUiState(),
                onDraftChanged = {},
                onSubmit = {},
                onMicTap = { permissionRequests += 1 },
                onSpeakLatest = {},
                onStopSpeaking = {},
            )
        }

        composeRule.onNodeWithText("语音输入").performClick()
        composeRule.onNodeWithText(
            "语音由设备上的 Android 语音服务识别，可能由该服务处理或传输音频。识别后的文字会交给当前助手处理。",
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, permissionRequests) }

        composeRule.onNodeWithText("继续").performClick()
        composeRule.runOnIdle { assertEquals(1, permissionRequests) }
        composeRule.onNodeWithText("语音输入").performClick()
        composeRule.onNodeWithText("开始语音输入").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(2, permissionRequests) }
    }

    @Test
    fun cancellingVoiceDisclosureDoesNotRequestMicrophone() {
        var permissionRequests = 0
        composeRule.setContent {
            ConversationScreen(
                state = ConversationUiState(),
                onDraftChanged = {},
                onSubmit = {},
                onMicTap = { permissionRequests++ },
                onSpeakLatest = {},
                onStopSpeaking = {},
            )
        }
        composeRule.onNodeWithText("语音输入").performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("开始语音输入").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, permissionRequests) }
        composeRule.onNodeWithText("语音输入").performClick()
        composeRule.onNodeWithText("开始语音输入").assertIsDisplayed()
    }

    @Test
    fun unavailableSpeechServiceShowsManualInputAndSubmitsTypedText() {
        val state = mutableStateOf(ConversationUiState())
        val submitted = mutableListOf<String>()
        var dismissed = 0
        var microphoneRequests = 0
        composeRule.setContent {
            ConversationScreen(
                state = state.value,
                onDraftChanged = { state.value = state.value.copy(draft = it) },
                onSubmit = { submitted += it },
                onMicTap = { microphoneRequests++ },
                onSpeakLatest = {},
                onStopSpeaking = {},
                showLiveVoiceSheet = true,
                onDismissLiveVoiceSheet = { dismissed++ },
            )
        }
        composeRule.onNodeWithText("改用文字输入").assertIsDisplayed()
        composeRule.onNodeWithText(
            "此设备暂时没有可用的语音识别服务。你可以输入指令，也可以使用键盘自带的语音输入。",
        ).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("整理会议纪要")
        composeRule.onNodeWithText("发送").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("整理会议纪要"), submitted)
            assertEquals(1, dismissed)
            assertEquals(0, microphoneRequests)
        }
    }
}
