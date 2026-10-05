package com.evax.mobile.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.ConversationUiState
import com.evax.mobile.presentation.ListeningStage
import com.evax.mobile.ui.theme.EvaXTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CompanionBehaviorInteractionTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setDefaultPreferences() = restorePreferences()
    @After fun restorePreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("companion_behavior", Context.MODE_PRIVATE).edit()
            .putBoolean("reduce_motion", false).putBoolean("idle_scenes", true).commit()
    }

    @Test fun listeningDisplaysActualSubphase() {
        val state = mutableStateOf(ConversationUiState(phase = AssistantPhase.LISTENING, listeningStage = ListeningStage.PREPARING))
        render { state.value }
        rule.onNodeWithText("正在准备麦克风").assertIsDisplayed()
        rule.onNodeWithText("请说话").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(listeningStage = ListeningStage.READY) }
        rule.onNodeWithText("请说话").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(listeningStage = ListeningStage.SPEAKING, draft = "帮我整理今天的安排") }
        rule.onNodeWithText("正在聆听").assertIsDisplayed()
        rule.onNodeWithText("“帮我整理今天的安排”").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(listeningStage = ListeningStage.RECOGNIZING) }
        rule.onNodeWithText("正在识别").assertIsDisplayed()
    }

    @Test fun openingTaskCardKeepsAvatarLayoutFixed() {
        val state = mutableStateOf(ConversationUiState())
        render { state.value }
        val original = rule.onNodeWithTag("assistant-avatar").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            state.value = state.value.copy(phase = AssistantPhase.EXECUTING, isProcessing = true, currentStep = "正在读取文件")
        }
        val executing = rule.onNodeWithTag("assistant-avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(original.top, executing.top, 0.1f)
        assertEquals(original.height, executing.height, 0.1f)
    }

    @Test fun surfingPreviewUsesLocalSceneAndVoiceDisclosureInterruptsIt() {
        rule.mainClock.autoAdvance = false
        render { ConversationUiState() }
        openSettings()
        rule.onNodeWithText("预览网上冲浪").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("avatar-idle-scene").assertIsDisplayed()
        rule.onNodeWithText("语音输入").performClick()
        rule.mainClock.advanceTimeBy(250)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        rule.onNodeWithText("开始语音输入").assertIsDisplayed()
    }

    @Test fun taskAndQueuedSpeechCancelPreview() {
        rule.mainClock.autoAdvance = false
        val state = mutableStateOf(ConversationUiState())
        render { state.value }
        openSettings()
        rule.onNodeWithText("预览看书").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("avatar-idle-scene").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(voicePlayback = state.value.voicePlayback.copy(queuedCount = 1)) }
        rule.mainClock.advanceTimeBy(250)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(voicePlayback = state.value.voicePlayback.copy(queuedCount = 0)) }
        openSettings()
        rule.onNodeWithText("预览接星星").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("avatar-idle-scene").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(phase = AssistantPhase.THINKING, isProcessing = true) }
        rule.mainClock.advanceTimeBy(250)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
    }

    @Test fun reducedMotionDisablesAllPreviewsAndPersistsPreference() {
        render { ConversationUiState() }
        openSettings()
        rule.onNodeWithTag("reduce-motion-enabled").performScrollTo().performClick()
        rule.onNodeWithText("预览网上冲浪").performScrollTo().assertIsNotEnabled()
        rule.runOnIdle {
            val stored = InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("companion_behavior", Context.MODE_PRIVATE)
            assertEquals(true, stored.getBoolean("reduce_motion", false))
        }
    }

    private fun openSettings() {
        rule.onNodeWithText("工具").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("设置").performClick()
        rule.mainClock.advanceTimeBy(200)
    }

    private fun render(state: () -> ConversationUiState) {
        rule.setContent {
            EvaXTheme {
                ConversationScreen(state = state(), onDraftChanged = {}, onSubmit = {}, onMicTap = {}, onSpeakLatest = {}, onStopSpeaking = {})
            }
        }
    }
}
