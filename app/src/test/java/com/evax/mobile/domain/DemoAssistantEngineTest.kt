package com.evax.mobile.domain

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAssistantEngineTest {
    private val engine = DemoAssistantEngine()

    @Test
    fun schedulePromptEmitsLabeledSampleSchedule() = runTest {
        val events = engine.respond("今天下午我有什么事情？").toList()
        val result = (events.last() as AssistantEvent.Completed).result

        assertEquals("下午 3 点有客户需求讨论，5 点有项目复盘。", result.text)
        assertEquals("演示数据", result.sampleLabel)
        assertTrue(result.isSample)
        assertEquals(AssistantSource.LOCAL_DEMO, result.source)
        assertEquals(AssistantSource.LOCAL_DEMO, (events.first() as AssistantEvent.SourceChanged).source)
    }

    @Test
    fun meetingPromptEmitsThreeStepsAndThreeFollowUps() = runTest {
        val events = engine.respond("帮我整理刚才的会议纪要").toList()
        val steps = events.filterIsInstance<AssistantEvent.Progress>().map { it.step }
        val result = (events.last() as AssistantEvent.Completed).result

        assertEquals(listOf("整理会议内容", "提取待办", "生成摘要"), steps)
        assertEquals(3, result.followUps.size)
        assertEquals("演示数据", result.sampleLabel)
    }

    @Test
    fun unsupportedPromptEmitsDemoFallback() = runTest {
        val events = engine.respond("给我讲个笑话").toList()
        val result = (events.last() as AssistantEvent.Completed).result

        assertTrue(result.text.contains("办公问答"))
        assertTrue(result.text.contains("会议纪要"))
        assertEquals("演示数据", result.sampleLabel)
    }

    @Test
    fun meetingIntentTakesPriorityOverScheduleWords() = runTest {
        val events = engine.respond("今天下午的会议纪要").toList()
        val steps = events.filterIsInstance<AssistantEvent.Progress>().map { it.step }
        val result = (events.last() as AssistantEvent.Completed).result

        assertEquals(listOf("整理会议内容", "提取待办", "生成摘要"), steps)
        assertTrue(result.text != "下午 3 点有客户需求讨论，5 点有项目复盘。")
    }
}
