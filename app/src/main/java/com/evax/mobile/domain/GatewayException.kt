package com.evax.mobile.domain

class GatewayException(
    val code: String,
    val userMessage: String,
    cause: Throwable? = null,
    val outcome: AssistantOutcome? = null,
    val taskId: String? = null,
    val requestId: String? = null,
    val evidence: ExecutionEvidence? = null,
) : Exception(userMessage, cause) {
    // 仅明确发生在执行前的失败允许快捷重试，未知错误不能造成重复电脑操作。
    val canRetryTask: Boolean get() = code.lowercase() in setOf(
        "not_configured", "invalid_endpoint", "invalid_token", "storage_read_failed", "storage_write_failed",
        "unconfigured", "workbuddy_unconfigured", "workbuddy_not_ready",
        "unauthorized", "authorization_required", "workbuddy_unauthorized", "auth_required", "scope_required",
        "pairing_failed", "pairing_required", "origin_denied", "offline", "workbuddy_offline",
        "connection_failed", "http_error", "invalid_request",
    )
}
