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
}
