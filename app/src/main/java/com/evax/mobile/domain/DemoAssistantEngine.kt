package com.evax.mobile.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DemoAssistantEngine(
    private val stepDelayMillis: Long = 420L,
) : AssistantEngine {
    override fun respond(prompt: String): Flow<AssistantEvent> = flow {
        emit(AssistantEvent.SourceChanged(AssistantSource.LOCAL_DEMO, "本地演示"))
        when {
            prompt.contains("会议") || prompt.contains("纪要") -> emitMeetingSummary()
            prompt.contains("安排") || prompt.contains("下午") || prompt.contains("日程") -> emitSchedule()
            prompt.contains("屏幕") || prompt.contains("截屏") -> emitScreenAnalysis()
            prompt.contains("Agent", ignoreCase = true) || prompt.contains("电脑") -> emitAgentStatus()
            prompt.contains("发消息") || prompt.contains("钉钉") || prompt.contains("通知") || prompt.contains("联系") ->
                emitDingTalkMessage(prompt)
            prompt.contains("方案") || prompt.contains("周报") || prompt.contains("报告") || prompt.contains("表格") || prompt.contains("文档") ->
                emitDocumentCreation(prompt)
            else -> emitFallback(prompt)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitMeetingSummary() {
        MEETING_STEPS.forEachIndexed { index, step ->
            emit(AssistantEvent.Progress(step, index + 1, MEETING_STEPS.size))
            delay(stepDelayMillis)
        }
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "会议已整理为演示摘要：讨论了当前阶段进度与主要风险，请确认负责人和完成时间。",
                    sampleLabel = SAMPLE_LABEL,
                    isSample = true,
                    source = AssistantSource.LOCAL_DEMO,
                    outcome = AssistantOutcome.TASK_SUCCEEDED,
                    followUps = listOf(
                        "确认各项待办的负责人",
                        "补充每项任务的完成时间",
                        "同步风险与所需支持",
                    ),
                ),
            ),
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitSchedule() {
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "下午 3 点有客户需求讨论，5 点有项目复盘。",
                    sampleLabel = SAMPLE_LABEL,
                    isSample = true,
                    source = AssistantSource.LOCAL_DEMO,
                    outcome = AssistantOutcome.TASK_SUCCEEDED,
                ),
            ),
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitScreenAnalysis() {
        SCREEN_STEPS.forEachIndexed { index, step ->
            emit(AssistantEvent.Progress(step, index + 1, SCREEN_STEPS.size))
            delay(stepDelayMillis)
        }
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "已读取电脑当前活动窗口（IDE 与需求文档）：检测到正在调试 EVA-X 桌面伴侣模块，无编译报错。",
                    sampleLabel = SAMPLE_LABEL,
                    isSample = true,
                    source = AssistantSource.LOCAL_DEMO,
                    outcome = AssistantOutcome.TASK_SUCCEEDED,
                    followUps = listOf(
                        "一键生成当前代码变更摘要",
                        "将屏幕选中内容发送至知识库",
                    ),
                ),
            ),
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitAgentStatus() {
        AGENT_STEPS.forEachIndexed { index, step ->
            emit(AssistantEvent.Progress(step, index + 1, AGENT_STEPS.size))
            delay(stepDelayMillis)
        }
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "电脑端 AI Agent 运行正常：MCP 桥接已就绪，后台有 1 项自动化任务已完成，随时可接收语音指令。",
                    sampleLabel = SAMPLE_LABEL,
                    isSample = true,
                    source = AssistantSource.LOCAL_DEMO,
                    outcome = AssistantOutcome.TASK_SUCCEEDED,
                    followUps = listOf(
                        "查看最近一次 Agent 执行日志",
                        "唤醒电脑端 WorkBuddy 协同任务",
                    ),
                ),
            ),
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitDingTalkMessage(prompt: String) {
        val steps = listOf("解析钉钉联系人与语境", "生成待发送消息卡片")
        steps.forEachIndexed { index, step ->
            emit(AssistantEvent.Progress(step, index + 1, steps.size))
            delay(stepDelayMillis)
        }
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "已为你准备好钉钉协同指令：「$prompt」。确认后将通过电脑端千问办公自动发送。",
                    sampleLabel = SAMPLE_LABEL,
                    isSample = true,
                    source = AssistantSource.LOCAL_DEMO,
                    outcome = AssistantOutcome.TASK_SUCCEEDED,
                    followUps = listOf(
                        "确认立即发送该钉钉消息",
                        "同时在日历中添加跟进提醒",
                    ),
                ),
            ),
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitDocumentCreation(prompt: String) {
        val steps = listOf("检索工作区上下文", "生成结构化文档与表格")
        steps.forEachIndexed { index, step ->
            emit(AssistantEvent.Progress(step, index + 1, steps.size))
            delay(stepDelayMillis)
        }
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "已根据你的语音指令「$prompt」在电脑端生成初稿文档，并同步至千问办公工作台。",
                    sampleLabel = SAMPLE_LABEL,
                    isSample = true,
                    source = AssistantSource.LOCAL_DEMO,
                    outcome = AssistantOutcome.TASK_SUCCEEDED,
                    followUps = listOf(
                        "在电脑端打开并预览文档",
                        "一键转发至项目群讨论",
                    ),
                ),
            ),
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitFallback(prompt: String) {
        emit(
            AssistantEvent.Progress("理解语音指令并同步工作台", 1, 1),
        )
        delay(stepDelayMillis / 2)
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "已收到你的指令：「$prompt」。当前本地演示支持办公问答、会议纪要整理及电脑协同演示，你可以试试说“查今天的安排”或“整理会议纪要”。",
                    sampleLabel = SAMPLE_LABEL,
                    isSample = true,
                    source = AssistantSource.LOCAL_DEMO,
                    outcome = AssistantOutcome.TASK_SUCCEEDED,
                    followUps = listOf(
                        "试试说：今天下午有什么安排",
                        "试试说：帮我整理刚才的会议纪要",
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val SAMPLE_LABEL = "演示数据"
        val MEETING_STEPS = listOf("整理会议内容", "提取待办", "生成摘要")
        val SCREEN_STEPS = listOf("捕获电脑活动窗口", "OCR 与视觉上下文解析")
        val AGENT_STEPS = listOf("查询本地 MCP 网关", "同步电脑端 Agent 队列")
    }
}
