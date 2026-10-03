package com.evax.mobile.platform.voice

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
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
            "语音识别由设备上安装的 Android 语音服务处理。该服务可能按照自己的行为对音频进行处理或传输；EVA-X 演示版不会把音频发送到 EVA-X 服务器。",
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, permissionRequests) }

        composeRule.onNodeWithText("继续").performClick()
        composeRule.runOnIdle { assertEquals(1, permissionRequests) }
    }
}
