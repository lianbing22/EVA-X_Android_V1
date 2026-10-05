package com.evax.mobile.domain

import org.junit.Assert.*
import org.junit.Test

class GatewayConnectionConfigTest {
    @Test
    fun acceptsExplicitBridgeAddressAndTrimsCopyWhitespace() {
        assertEquals(
            GatewayConnectionConfig("http://192.168.1.20:3099", "pairing-code"),
            GatewayConnectionConfig(" http://192.168.1.20:3099/ ", " pairing-code ").validated(),
        )
    }

    @Test
    fun rejectsAddressesWithEmbeddedCredentialsParametersRedirectFragmentsOrInvalidPort() {
        for (endpoint in listOf(
            "192.168.1.20:3099", "ftp://192.168.1.20:3099", "http://user:secret@192.168.1.20:3099",
            "http://192.168.1.20:3099?token=secret", "http://192.168.1.20:3099#ignored", "http://192.168.1.20:70000",
        )) {
            try {
                GatewayConnectionConfig(endpoint, "pairing-code").validated()
                fail("无效连接地址不应发起网络请求")
            } catch (failure: GatewayException) {
                assertEquals("invalid_endpoint", failure.code)
            }
        }
    }

    @Test
    fun missingPairingCodeAndHeaderControlCharactersAreRejected() {
        for ((token, expectedCode) in listOf("" to "not_configured", "pairing\r\nInjected: header" to "invalid_token")) {
            try {
                GatewayConnectionConfig("http://192.168.1.20:3099", token).validated()
                fail("缺失或无效配对码不应提交")
            } catch (failure: GatewayException) {
                assertEquals(expectedCode, failure.code)
            }
        }
    }

    @Test
    fun tunnelBeaconEncryptsDecryptsAndRejectsWrongPairingToken() {
        val token = "YvpyZ4nG0d_AlDqMP4MjWW_oDi7kfMrPF3O2x-PgTnk"
        val topic = TunnelBeaconResolver.deriveDiscoveryTopic(token)
        assertTrue(topic.matches(Regex("^evax-wb-[0-9a-f]{28}$")))

        val envelope = TunnelBeaconResolver.encryptTunnelBeacon(
            pairingToken = token,
            tunnelUrl = "https://a4d12a64ae15ca.lhr.life",
            lanUrl = "http://192.168.2.4:3099",
            updatedAt = "2026-10-05T05:27:12.197Z",
        )
        assertFalse(envelope.contains("lhr.life"))

        val decrypted = TunnelBeaconResolver.decryptTunnelBeacon(token, envelope)
        assertNotNull(decrypted)
        assertEquals("https://a4d12a64ae15ca.lhr.life", decrypted?.tunnelUrl)
        assertEquals("http://192.168.2.4:3099", decrypted?.lanUrl)

        assertNull(TunnelBeaconResolver.decryptTunnelBeacon("wrong-pairing-token", envelope))
    }
}

