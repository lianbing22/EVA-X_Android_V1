package com.evax.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class EvaXEndToEndTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scheduleExampleChipRendersSampleAnswer() {
        composeRule.onNodeWithText("查今天的安排").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("下午 3 点有客户需求讨论，5 点有项目复盘。")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("下午 3 点有客户需求讨论，5 点有项目复盘。").assertIsDisplayed()
        composeRule.onNodeWithText("演示数据").assertIsDisplayed()
    }

    @Test
    fun meetingPromptRendersStepsAndFollowUps() {
        composeRule.onNodeWithText("整理会议纪要").performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("会议已整理为演示摘要：讨论了当前阶段进度与主要风险，请确认负责人和完成时间。")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("整理会议内容").assertIsDisplayed()
        composeRule.onNodeWithText("提取待办").assertIsDisplayed()
        composeRule.onNodeWithText("生成摘要").assertIsDisplayed()
        composeRule.onNodeWithText("确认各项待办的负责人").assertIsDisplayed()
        composeRule.onNodeWithText("补充每项任务的完成时间").assertIsDisplayed()
        composeRule.onNodeWithText("同步风险与所需支持").assertIsDisplayed()
    }
}
