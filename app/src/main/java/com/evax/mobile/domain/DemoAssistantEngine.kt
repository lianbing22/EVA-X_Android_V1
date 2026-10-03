package com.evax.mobile.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DemoAssistantEngine(
    private val stepDelayMillis: Long = 420L,
) : AssistantEngine {
    override fun respond(prompt: String): Flow<AssistantEvent> = flow {
        when {
            prompt.contains("会议") || prompt.contains("纪要") -> emitMeetingSummary()
            prompt.contains("安排") || prompt.contains("下午") -> emitSchedule()
            prompt.contains("屏幕") || prompt.contains("截屏") -> emitScreenAnalysis()
            prompt.contains("Agent", ignoreCase = true) || prompt.contains("电脑") -> emitAgentStatus()
            else -> emitFallback()
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
                    followUps = listOf(
                        "查看最近一次 Agent 执行日志",
                        "唤醒电脑端 WorkBuddy 协同任务",
                    ),
                ),
            ),
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantEvent>.emitFallback() {
        emit(
            AssistantEvent.Completed(
                AssistantResult(
                    text = "当前演示版仅支持办公问答和会议纪要整理演示。你可以试试“查今天的安排”或“整理会议纪要”。",
                    sampleLabel = SAMPLE_LABEL,
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
