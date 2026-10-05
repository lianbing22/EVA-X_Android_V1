package com.evax.mobile.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.AvatarFeedback
import com.evax.mobile.presentation.AvatarFeedbackKind
import com.evax.mobile.presentation.ListeningStage
import com.evax.mobile.ui.theme.EvaXTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** Local visual fixtures; these never submit commands to a computer or a speech service. */
class CompanionBehaviorCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun automaticStoryStartsAfterRealIdleAndListeningInterruptsIt() {
        rule.mainClock.autoAdvance = false
        val phase = mutableStateOf(AssistantPhase.IDLE)
        rule.setContent {
            EvaXTheme {
                AssistantAvatar(
                    phase.value, modifier = Modifier.fillMaxSize().background(Color.Black),
                    idleScenesAllowed = phase.value == AssistantPhase.IDLE,
                    listeningStage = if (phase.value == AssistantPhase.LISTENING) ListeningStage.READY else ListeningStage.NONE,
                )
            }
        }
        val started = android.os.SystemClock.elapsedRealtime()
        var found = false
        while (android.os.SystemClock.elapsedRealtime() - started < 70_000L) {
            rule.mainClock.advanceTimeBy(100)
            if (rule.onAllNodesWithTag("avatar-idle-scene").fetchSemanticsNodes().isNotEmpty()) {
                found = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("No automatic scene within the real 45–60 second eligibility window", found)
        assertTrue("Automatic scene started before the minimum quiet window", android.os.SystemClock.elapsedRealtime() - started >= 44_000L)
        rule.mainClock.advanceTimeBy(800)
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "behavior-qa")
        check(output.exists() || output.mkdirs())
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(output, "automatic-idle-story.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        rule.runOnIdle { phase.value = AssistantPhase.LISTENING }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(8_000)
        rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
    }

    @Test fun captureStoriesFeedbackAndListeningStages() {
        rule.mainClock.autoAdvance = false
        val scene = mutableStateOf<IdleScene?>(null)
        val phase = mutableStateOf(AssistantPhase.IDLE)
        val stage = mutableStateOf(ListeningStage.NONE)
        val feedback = mutableStateOf<AvatarFeedback?>(null)
        rule.setContent {
            EvaXTheme {
                AssistantAvatar(
                    phase = phase.value,
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    eyeStyle = EvaEyeStyle.CYBER_COZMO,
                    previewScene = scene.value,
                    onPreviewFinished = { scene.value = null },
                    listeningStage = stage.value,
                    feedback = feedback.value,
                    micLevel = if (stage.value == ListeningStage.SPEAKING) 0.65f else 0f,
                )
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "behavior-qa")
        check(output.exists() || output.mkdirs())
        fun capture(name: String) {
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        IdleScene.entries.forEach { story ->
            rule.runOnIdle { scene.value = story }
            rule.mainClock.advanceTimeBy(600)
            rule.onNodeWithTag("avatar-idle-scene").assertExists()
            capture("${story.name.lowercase()}-01")
            rule.mainClock.advanceTimeBy(1_600)
            capture("${story.name.lowercase()}-02")
            rule.mainClock.advanceTimeBy(1_600)
            capture("${story.name.lowercase()}-03")
            rule.runOnIdle { scene.value = null }
            rule.mainClock.advanceTimeBy(250)
            rule.onNodeWithTag("avatar-idle-scene").assertDoesNotExist()
        }
        listOf(ListeningStage.PREPARING, ListeningStage.READY, ListeningStage.SPEAKING, ListeningStage.RECOGNIZING).forEach { listening ->
            rule.runOnIdle { phase.value = AssistantPhase.LISTENING; stage.value = listening }
            rule.mainClock.advanceTimeBy(450)
            capture("listening-${listening.name.lowercase()}")
        }
        AvatarFeedbackKind.entries.forEachIndexed { index, kind ->
            rule.runOnIdle {
                stage.value = ListeningStage.NONE
                phase.value = if (kind == AvatarFeedbackKind.FAILED) AssistantPhase.ERROR else AssistantPhase.COMPLETED
                feedback.value = AvatarFeedback(kind, index.toLong() + 100)
            }
            rule.mainClock.advanceTimeBy(700)
            capture("feedback-${kind.name.lowercase()}")
            rule.runOnIdle { feedback.value = null }
            rule.mainClock.advanceTimeBy(300)
        }
    }
}
