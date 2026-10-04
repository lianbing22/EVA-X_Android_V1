package com.evax.mobile.ui

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import com.evax.mobile.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionInputLayoutTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun landscapeKeyboardKeepsInputAndSendReadable() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitUntil(5_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("对话详情").performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("landscape line one\nline two\nline three")
        composeRule.onNodeWithText("发送").assertIsDisplayed()
        val sendBounds = composeRule.onNodeWithText("发送").fetchSemanticsNode().boundsInRoot
        val inputBounds = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        assertTrue("发送文字不能被挤成一条线", sendBounds.height >= 16 * density)
        assertTrue("输入区需保留可阅读高度", inputBounds.height >= 56 * density)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-qa")
        check(output.exists() || output.mkdirs())
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(output, "landscape-keyboard.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
