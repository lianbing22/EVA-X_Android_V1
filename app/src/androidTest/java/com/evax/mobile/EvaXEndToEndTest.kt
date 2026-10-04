package com.evax.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class EvaXEndToEndTest {
    // MainActivity 在 instrumentation 下固定使用 DemoAssistantEngine，不发送电脑任务。
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scheduleExampleChipRendersSampleAnswer() {
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("查今天的安排").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("下午 3 点有客户需求讨论，5 点有项目复盘。")
                .fetchSemanticsNodes().isNotEmpty()
        }
        openConversationDetails()

        composeRule.onNodeWithText("本地演示 · 示例内容").assertIsDisplayed()
        composeRule.onAllNodesWithText("下午 3 点有客户需求讨论，5 点有项目复盘。").onLast().assertIsDisplayed()
        composeRule.onAllNodesWithText("演示数据").onLast().assertIsDisplayed()
    }

    @Test
    fun meetingPromptRendersStepsAndFollowUps() {
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("整理会议纪要").performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("会议已整理为演示摘要：讨论了当前阶段进度与主要风险，请确认负责人和完成时间。")
                .fetchSemanticsNodes().isNotEmpty()
        }
        openConversationDetails()
        composeRule.onNodeWithText("本地演示 · 示例内容").assertIsDisplayed()

        assertConversationTextVisible("整理会议内容")
        assertConversationTextVisible("提取待办")
        assertConversationTextVisible("生成摘要")
        assertConversationTextVisible("确认各项待办的负责人")
        assertConversationTextVisible("补充每项任务的完成时间")
        assertConversationTextVisible("同步风险与所需支持")
    }

    private fun openConversationDetails() {
        composeRule.onNodeWithText("工具").performClick()
        composeRule.onNodeWithText("对话详情").performClick()
    }

    private fun assertConversationTextVisible(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }
}
