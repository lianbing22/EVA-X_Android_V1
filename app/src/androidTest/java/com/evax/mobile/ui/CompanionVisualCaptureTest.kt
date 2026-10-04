package com.evax.mobile.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evax.mobile.domain.AssistantSource
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.ConversationMessage
import com.evax.mobile.presentation.ConversationUiState
import com.evax.mobile.presentation.MessageRole
import com.evax.mobile.ui.theme.EvaXTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Render state fixtures without connecting to a computer or speech service. */
@RunWith(AndroidJUnit4::class)
class CompanionVisualCaptureTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureAllExpressions() {
        composeRule.activityRule.scenario.onActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        val current = mutableStateOf(ConversationUiState())
        composeRule.setContent {
            EvaXTheme {
                ConversationScreen(
                    state = current.value,
                    onDraftChanged = {}, onSubmit = {}, onMicTap = {},
                    onSpeakLatest = {}, onStopSpeaking = {}, liveMicLevel = 0.65f,
                )
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "visual-qa")
        check(output.exists() || output.mkdirs())
        AssistantPhase.entries.forEach { phase ->
            composeRule.runOnIdle {
                current.value = ConversationUiState(
                    phase = phase,
                    source = AssistantSource.LOCAL_DEMO,
                    isProcessing = phase == AssistantPhase.THINKING || phase == AssistantPhase.EXECUTING,
                    currentStep = if (phase == AssistantPhase.EXECUTING) "提取会议中的关键结论" else null,
                    progressSteps = if (phase == AssistantPhase.EXECUTING) listOf("读取会议记录", "提取会议中的关键结论") else emptyList(),
                    completedProgressSteps = if (phase == AssistantPhase.EXECUTING) listOf("读取会议记录") else emptyList(),
                    progressIndex = if (phase == AssistantPhase.EXECUTING) 2 else 0,
                    progressTotal = if (phase == AssistantPhase.EXECUTING) 4 else 0,
                    notice = if (phase == AssistantPhase.ERROR) "这次没听清，可以再说一次或改用文字。" else null,
                    messages = if (phase == AssistantPhase.COMPLETED) listOf(
                        ConversationMessage(1, MessageRole.ASSISTANT, "会议纪要整理好了，需要看看待办吗？", isSample = true),
                    ) else emptyList(),
                )
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${phase.name.lowercase()}.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }
}
