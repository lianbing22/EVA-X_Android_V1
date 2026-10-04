package com.evax.mobile.platform.gateway

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evax.mobile.domain.GatewayConnectionConfig
import com.evax.mobile.domain.GatewayException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GatewayConfigStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun newStoreStartsWithoutGuessedEndpointOrToken() {
        val id = UUID.randomUUID().toString()
        val store = GatewayConfigStore(context, "test-gateway-$id", "test-gateway-key-$id")
        assertEquals(GatewayConnectionConfig(), store.load())
    }

    @Test
    fun keystoreCiphertextSurvivesNewStoreAndContainsNoPlaintextToken() {
        val id = UUID.randomUUID().toString()
        val preferencesName = "test-gateway-$id"
        val keyAlias = "test-gateway-key-$id"
        val expected = GatewayConnectionConfig("http://192.168.20.25:3099", "private-test-pairing-secret")
        GatewayConfigStore(context, preferencesName, keyAlias).save(expected)
        assertEquals(expected, GatewayConfigStore(context, preferencesName, keyAlias).load())
        val stored = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).getString("encrypted_config", null).orEmpty()
        assertFalse(stored.contains(expected.pairingToken))
        assertFalse(stored.contains(expected.endpoint))
        assertFalse(expected.toString().contains(expected.pairingToken))
    }

    @Test
    fun corruptStorageReportsErrorAndCanBeReplacedByExplicitSave() {
        val id = UUID.randomUUID().toString()
        val preferencesName = "test-gateway-$id"
        val store = GatewayConfigStore(context, preferencesName, "test-gateway-key-$id")
        val expected = GatewayConnectionConfig("http://192.168.20.25:3099", "private-test-pairing-secret")
        store.save(expected)
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().putString("encrypted_config", "corrupt").commit()
        try {
            store.load()
            fail("损坏密文不能静默降级成另一份连接配置")
        } catch (failure: GatewayException) {
            assertEquals("storage_read_failed", failure.code)
        }
        store.save(expected)
        assertEquals(expected, store.load())
    }
}
