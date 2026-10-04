package com.evax.mobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.evax.mobile.domain.GatewayConnectionConfig
import com.evax.mobile.domain.GatewayConnectionState
import com.evax.mobile.domain.GatewayConnectionStatus
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.ConversationMessage
import com.evax.mobile.presentation.ConversationUiState
import com.evax.mobile.presentation.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Uses only composable callbacks and fake connection results; no computer or office API calls. */
class GatewayConnectionPanelTest {
    @get:Rule val composeRule = createComposeRule()
    private val saved = GatewayConnectionConfig("http://192.168.1.20:3099", "fake-pairing-token")
    private val ready = GatewayConnectionStatus(
        state = GatewayConnectionState.READY,
        message = "WorkBuddy 助理在线",
        configured = true,
        authorized = true,
        online = true,
        checkedAt = 1L,
    )

    @Test
    fun pairingTokenIsMaskedAndEditingInvalidatesEvenWhenRestored() {
        composeRule.setContent {
            GatewayConnectionPanel(saved, ready, false, {}, {}, {})
        }
        composeRule.onNodeWithTag("gateway_pairing_token").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNodeWithText("在线").assertIsDisplayed()
        composeRule.onNodeWithTag("gateway_endpoint").performTextReplacement("http://192.168.1.21:3099")
        composeRule.onNodeWithText("在线").assertDoesNotExist()
        composeRule.onNodeWithText("输入已改变，请重新测试连接").assertIsDisplayed()
        composeRule.onNodeWithTag("gateway_endpoint").performTextReplacement(saved.endpoint)
        composeRule.onNodeWithText("在线").assertDoesNotExist()
    }

    @Test
    fun testUsesCurrentDraftAndDoesNotSaveOrSubmitTask() {
        val state = mutableStateOf(GatewayConnectionStatus())
        var tested: GatewayConnectionConfig? = null
        var saves = 0
        composeRule.setContent {
            GatewayConnectionPanel(
                config = saved, status = state.value, isProcessing = false,
                onSave = { saves++ },
                onTest = { tested = it; state.value = ready.copy(checkedAt = 2L) },
                onBack = {},
            )
        }
        composeRule.onNodeWithTag("gateway_endpoint").performTextReplacement("http://192.168.1.30:3099")
        composeRule.onNodeWithTag("gateway_test").performScrollTo().performClick()
        composeRule.onNodeWithText("当前输入已验证，保存后生效").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(saved.copy(endpoint = "http://192.168.1.30:3099"), tested)
            assertEquals(0, saves)
        }
    }

    @Test
    fun unsavedTestResultDoesNotVerifyDifferentSavedConfig() {
        composeRule.setContent {
            GatewayConnectionPanel(
                config = saved, status = ready, isProcessing = false,
                onSave = {}, onTest = {}, onBack = {},
                verifiedInput = saved.copy(endpoint = "http://192.168.1.99:3099"),
            )
        }
        composeRule.onNodeWithText("在线").assertDoesNotExist()
        composeRule.onNodeWithText("输入已改变，请重新测试连接").assertIsDisplayed()
    }

    @Test
    fun savingOnlySendsTrimmedGatewayConfig() {
        var stored: GatewayConnectionConfig? = null
        composeRule.setContent { GatewayConnectionPanel(saved, GatewayConnectionStatus(), false, { stored = it }, {}, {}) }
        composeRule.onNodeWithTag("gateway_endpoint").performTextReplacement("  http://192.168.1.40:3099  ")
        composeRule.onNodeWithTag("gateway_pairing_token").performScrollTo().performTextReplacement("  another-fake-token  ")
        composeRule.onNodeWithTag("gateway_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(GatewayConnectionConfig("http://192.168.1.40:3099", "another-fake-token"), stored) }
    }

    @Test
    fun executionLocksInputsSaveAndTest() {
        var actions = 0
        composeRule.setContent { GatewayConnectionPanel(saved, ready, true, { actions++ }, { actions++ }, {}) }
        composeRule.onNodeWithTag("gateway_endpoint").assertIsNotEnabled()
        composeRule.onNodeWithTag("gateway_pairing_token").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("gateway_save").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("gateway_test").performScrollTo().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, actions) }
    }

    @Test
    fun legacy3088AddressCannotBeSavedOrTestedAsWorkBuddyConnection() {
        composeRule.setContent { GatewayConnectionPanel(saved, ready, false, {}, {}, {}) }
        composeRule.onNodeWithTag("gateway_endpoint").performTextReplacement("http://192.168.1.40:3088")
        composeRule.onNodeWithText("请使用 WorkBuddy 桥接地址，默认端口为 3099").assertIsDisplayed()
        composeRule.onNodeWithTag("gateway_save").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("gateway_test").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun unauthorizedAndOfflineHaveDistinctVisibleStates() {
        val state = mutableStateOf(ready.copy(state = GatewayConnectionState.ERROR, authorized = false, online = false, errorCode = "unauthorized"))
        composeRule.setContent { GatewayConnectionPanel(saved, state.value, false, {}, {}, {}) }
        composeRule.onNodeWithText("未授权").assertIsDisplayed()
        composeRule.runOnIdle { state.value = ready.copy(state = GatewayConnectionState.ERROR, online = false, errorCode = "assistant_offline") }
        composeRule.onNodeWithText("助理离线").assertIsDisplayed()
    }

    @Test
    fun noticeOpensConnectionAndStatusToolNeverSubmitsPrompt() {
        var tasks = 0
        var checks = 0
        composeRule.setContent {
            ConversationScreen(
                state = ConversationUiState(phase = AssistantPhase.ERROR, notice = "电脑连接失败"),
                onDraftChanged = {}, onSubmit = { tasks++ }, onMicTap = {}, onSpeakLatest = {}, onStopSpeaking = {},
                gatewayConfig = saved,
                onTestGatewayConnection = { assertEquals(saved, it); checks++ },
            )
        }
        composeRule.onNodeWithText("电脑连接").performClick()
        composeRule.onNodeWithTag("gateway_endpoint").assertIsDisplayed()
        composeRule.onNodeWithText("收起").performClick()
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("检查电脑连接").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(0, tasks); assertEquals(1, checks) }
        composeRule.onNodeWithTag("gateway_endpoint").assertIsDisplayed()
    }

    @Test
    fun ambiguousTaskFailureOffersStatusCheckWithoutRetryingOfficeTask() {
        var tasks = 0
        var checks = 0
        composeRule.setContent {
            ConversationScreen(
                state = ConversationUiState(
                    phase = AssistantPhase.ERROR,
                    notice = "任务结果尚未确认",
                    canRetryTask = false,
                    messages = listOf(ConversationMessage(1L, MessageRole.USER, "起草一封邮件")),
                ),
                onDraftChanged = {}, onSubmit = { tasks++ }, onMicTap = {}, onSpeakLatest = {}, onStopSpeaking = {},
                gatewayConfig = saved,
                onTestGatewayConnection = { checks++ },
            )
        }
        composeRule.onNodeWithText("重试").assertDoesNotExist()
        composeRule.onNodeWithText("请先在电脑端确认状态").assertIsDisplayed()
        composeRule.onNodeWithText("电脑连接").assertIsDisplayed()
        composeRule.onNodeWithText("查看电脑状态").performClick()
        composeRule.onNodeWithTag("gateway_endpoint").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, tasks); assertEquals(1, checks) }
    }
}
