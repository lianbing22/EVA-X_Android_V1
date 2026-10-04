package com.evax.mobile.domain

enum class GatewayConnectionState {
    UNCONFIGURED,
    NOT_CHECKED,
    CHECKING,
    READY,
    ERROR,
}

data class GatewayConnectionStatus(
    val state: GatewayConnectionState = GatewayConnectionState.UNCONFIGURED,
    val message: String = "请先配置电脑桥接地址和配对码",
    val configured: Boolean = false,
    val authorized: Boolean = false,
    val online: Boolean = false,
    val checkedAt: Long? = null,
    val errorCode: String? = null,
) {
    val isTesting: Boolean get() = state == GatewayConnectionState.CHECKING

    companion object {
        fun notChecked(config: GatewayConnectionConfig): GatewayConnectionStatus =
            if (config.isConfigured) GatewayConnectionStatus(
                state = GatewayConnectionState.NOT_CHECKED,
                message = "配置已保存，尚未测试连接",
            ) else GatewayConnectionStatus()
    }
}
