package com.evax.mobile.domain

import java.net.URI

data class GatewayConnectionConfig(
    val endpoint: String = "",
    val pairingToken: String = "",
) {
    val isConfigured: Boolean get() = endpoint.isNotBlank() && pairingToken.isNotBlank()

    fun validated(): GatewayConnectionConfig {
        val address = endpoint.trim().trimEnd('/')
        val token = pairingToken.trim()
        if (address.isEmpty() || token.isEmpty()) {
            throw GatewayException("not_configured", "请先在设置中填写电脑桥接地址和配对码。")
        }
        val uri = try {
            URI(address)
        } catch (_: Exception) {
            throw GatewayException("invalid_endpoint", "电脑桥接地址格式不正确，请填写完整的 http:// 或 https:// 地址。")
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            uri.port !in -1..65535 || uri.port == 0 || uri.rawPath.orEmpty().contains("..")
        ) {
            throw GatewayException("invalid_endpoint", "电脑桥接地址格式不正确，请使用不含账号、查询参数或片段的 HTTP 地址。")
        }
        if (token.any { it.isISOControl() }) {
            throw GatewayException("invalid_token", "配对码含有无效字符，请重新复制。")
        }
        return copy(endpoint = address, pairingToken = token)
    }

    override fun toString(): String = "GatewayConnectionConfig(endpoint=$endpoint, pairingToken=<redacted>)"
}
