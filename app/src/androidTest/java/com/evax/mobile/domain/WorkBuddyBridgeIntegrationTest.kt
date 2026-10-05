package com.evax.mobile.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in Android → actual Node bridge → local HTTP contract fixture, never production. */
@RunWith(AndroidJUnit4::class)
class WorkBuddyBridgeIntegrationTest {
    @Test
    fun androidRequestReachesNodeAdapterAndReturnsTheMatchingReply() = runBlocking {
        val endpoint = InstrumentationRegistry.getArguments().getString("workbuddy_contract_endpoint")
        assumeTrue("Start the explicit local contract fixture and supply workbuddy_contract_endpoint", endpoint != null)
        val config = GatewayConnectionConfig(endpoint!!, "local-contract-fixture-pairing-token")
        val engine = GatewayAssistantEngine { config }
        assertEquals(GatewayConnectionState.READY, engine.testConnection(config).state)
        val events = engine.respond("EVA-X Android to Node contract check").toList()
        assertTrue(events.any { it is AssistantEvent.SourceChanged && it.source == AssistantSource.PC_GATEWAY })
        val result = events.filterIsInstance<AssistantEvent.Completed>().single().result
        assertTrue(result.text.contains("本地契约夹具"))
        assertTrue(result.text.contains("未执行真实电脑任务"))
        assertFalse(result.isSample)
        assertEquals(AssistantSource.PC_GATEWAY, result.source)
        assertEquals(AssistantOutcome.NEEDS_ATTENTION, result.outcome)
        assertEquals("NEEDS_ATTENTION", engine.testConnection(config).errorCode)
    }
}
