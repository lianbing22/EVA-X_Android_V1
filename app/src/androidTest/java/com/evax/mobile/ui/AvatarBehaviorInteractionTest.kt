package com.evax.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.AvatarFeedback
import com.evax.mobile.presentation.AvatarFeedbackKind
import com.evax.mobile.presentation.VoicePlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Uses local state only: no recognizer, TTS engine, network, or computer tasks. */
class AvatarBehaviorInteractionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allEightManualPreviewsPlayAndFinishWithAutomaticScenesDisabled() {
        rule.mainClock.autoAdvance = false
        val preview = mutableStateOf<IdleScene?>(null)
        var finished = 0
        rule.setContent {
            AssistantAvatar(AssistantPhase.IDLE, modifier = Modifier.fillMaxSize(),
                previewScene = preview.value, idleScenesAllowed = false,
                onPreviewFinished = { finished++; preview.value = null })
        }
        for (scene in IdleScene.entries) {
            rule.runOnIdle { preview.value = scene }
            rule.mainClock.advanceTimeBy(800)
            rule.onNodeWithTag("avatar-idle-scene").assertIsDisplayed()
            rule.onNodeWithText("休闲小剧场 · ${scene.label}").assertIsDisplayed()
            rule.mainClock.advanceTimeBy(scene.durationMillis + 100)
            rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        }
        rule.runOnIdle { assertEquals(8, finished) }
    }

    @Test fun touchInterruptsPreviewImmediatelyAndOldFramesNeverReturn() {
        rule.mainClock.autoAdvance = false
        val preview = mutableStateOf<IdleScene?>(IdleScene.SURFING)
        var touches = 0
        rule.setContent {
            AssistantAvatar(AssistantPhase.IDLE, modifier = Modifier.fillMaxSize().testTag("touch-avatar"),
                previewScene = preview.value, onPreviewFinished = { preview.value = null },
                onInteraction = { touches++; preview.value = null })
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("avatar-idle-scene").assertIsDisplayed()
        rule.onNodeWithTag("touch-avatar").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(8_000)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, touches) }
    }

    @Test fun queuedSpeechCancelsPreviewAndClearingQueueDoesNotResumeIt() {
        rule.mainClock.autoAdvance = false
        val preview = mutableStateOf<IdleScene?>(IdleScene.READING)
        val voice = mutableStateOf(VoicePlaybackState())
        rule.setContent {
            AssistantAvatar(AssistantPhase.IDLE, modifier = Modifier.fillMaxSize(),
                previewScene = preview.value, voicePlayback = voice.value,
                onPreviewFinished = { preview.value = null })
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("avatar-idle-scene").assertIsDisplayed()
        rule.runOnIdle { voice.value = VoicePlaybackState(queuedCount = 1) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        rule.runOnIdle { voice.value = VoicePlaybackState() }
        rule.mainClock.advanceTimeBy(8_000)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
    }

    @Test fun taskTakesControlWithoutResumingOldMiniScene() {
        rule.mainClock.autoAdvance = false
        val preview = mutableStateOf<IdleScene?>(IdleScene.STARS)
        val phase = mutableStateOf(AssistantPhase.IDLE)
        rule.setContent {
            AssistantAvatar(phase.value, modifier = Modifier.fillMaxSize(), previewScene = preview.value,
                onPreviewFinished = { preview.value = null })
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("avatar-idle-scene").assertIsDisplayed()
        rule.runOnIdle { phase.value = AssistantPhase.THINKING }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        rule.onNodeWithContentDescription("EVA 双眼，正在思考").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(8_000)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
    }

    @Test fun reducedMotionAndNonCompanionModesRejectPreviews() {
        rule.mainClock.autoAdvance = false
        val preview = mutableStateOf<IdleScene?>(null)
        val mode = mutableStateOf(EvaCompanionMode.COMPANION)
        val reduced = mutableStateOf(false)
        var finished = 0
        rule.setContent {
            AssistantAvatar(AssistantPhase.IDLE, modifier = Modifier.fillMaxSize(),
                companionMode = mode.value, reduceMotion = reduced.value, previewScene = preview.value,
                onPreviewFinished = { finished++; preview.value = null })
        }
        rule.runOnIdle { reduced.value = true; preview.value = IdleScene.COFFEE }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        for (blockedMode in listOf(EvaCompanionMode.DND, EvaCompanionMode.REST)) {
            rule.runOnIdle { reduced.value = false; mode.value = blockedMode; preview.value = IdleScene.GAMING }
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        }
        rule.runOnIdle { assertEquals(3, finished) }
    }

    @Test fun readyReplyAndConfirmedSuccessHaveSeparateAccessibleMeanings() {
        rule.mainClock.autoAdvance = false
        val feedback = mutableStateOf(AvatarFeedback(AvatarFeedbackKind.REPLY_READY, 1))
        rule.setContent {
            AssistantAvatar(AssistantPhase.COMPLETED, modifier = Modifier.fillMaxSize(), feedback = feedback.value)
        }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription("EVA 双眼，回复就绪").assertIsDisplayed()
        rule.runOnIdle { feedback.value = AvatarFeedback(AvatarFeedbackKind.TASK_SUCCEEDED, 2) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription("EVA 双眼，任务成功").assertIsDisplayed()
    }

    @Test fun lowerPriorityScriptsCannotReplaceFeedbackAndCancelledFramesCannotReturn() {
        rule.mainClock.autoAdvance = false
        lateinit var player: AvatarBehaviorPlayer
        rule.setContent {
            val scope = rememberCoroutineScope()
            player = remember(scope) { AvatarBehaviorPlayer(scope) }
        }
        rule.runOnIdle {
            player.play(idleSceneScript(IdleScene.GAMING), IdleScene.GAMING)
            player.play(feedbackScript(AvatarFeedbackKind.TASK_SUCCEEDED), requestedPriority = 3)
            assertNull(player.play(speechBeatScript(true), requestedPriority = 1))
        }
        rule.mainClock.advanceTimeBy(700)
        rule.runOnIdle {
            assertNull(player.frame?.scene)
            assertTrue(requireNotNull(player.frame).pose.smile > 0.9f)
            player.cancel()
            assertNull(player.frame)
        }
        rule.mainClock.advanceTimeBy(8_000)
        rule.runOnIdle { assertNull(player.frame) }
    }
}
