package com.evax.mobile.domain

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 仅连接通过配对的 WorkBuddy 官方桥接（支持局域网直连与配对码加密公网长连接隧道自动切换），不自动切换到演示数据。 */
class GatewayAssistantEngine(
    private val configProvider: () -> GatewayConnectionConfig,
    private val beaconResolver: (suspend (String) -> GatewayTunnelBeacon?)? = null,
    private val onEndpointDiscovered: ((String) -> Unit)? = null,
) : AssistantEngine {
    constructor(configProvider: () -> GatewayConnectionConfig) : this(
        configProvider = configProvider,
        beaconResolver = null,
        onEndpointDiscovered = null,
    )

    @Volatile
    private var cachedDiscoveredEndpoint: String? = null

    @Volatile
    var lastResolvedEndpoint: String? = null
        private set

    override fun respond(prompt: String): Flow<AssistantEvent> = flow {
        // 单次请求使用完整配置快照，保存新设置不会把正在执行的请求切到另一台电脑。
        val baseConfig = configProvider().validated()
        val (activeConfig, status) = resolveAndCheckConnection(baseConfig)
        if (status.state != GatewayConnectionState.READY) {
            throw GatewayException(status.errorCode ?: "connection_failed", status.message)
        }
        emit(AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, "电脑已连接"))
        streamResponse(activeConfig, prompt) { emit(it) }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(config: GatewayConnectionConfig): GatewayConnectionStatus = withContext(Dispatchers.IO) {
        try {
            val (_, status) = resolveAndCheckConnection(config.validated())
            status
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: GatewayException) {
            GatewayConnectionStatus(
                state = if (failure.code == "not_configured") GatewayConnectionState.UNCONFIGURED else GatewayConnectionState.ERROR,
                message = failure.userMessage,
                checkedAt = System.currentTimeMillis(),
                errorCode = failure.code,
            )
        } catch (failure: Exception) {
            val mapped = networkFailure(failure)
            GatewayConnectionStatus(
                state = GatewayConnectionState.ERROR,
                message = mapped.userMessage,
                checkedAt = System.currentTimeMillis(),
                errorCode = mapped.code,
            )
        }
    }

    private suspend fun resolveAndCheckConnection(
        baseConfig: GatewayConnectionConfig,
    ): Pair<GatewayConnectionConfig, GatewayConnectionStatus> {
        val primaryFailure: GatewayException = try {
            val status = checkConnection(baseConfig)
            lastResolvedEndpoint = baseConfig.endpoint
            return baseConfig to status
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: GatewayException) {
            if (!shouldAttemptRemoteDiscovery(failure) || beaconResolver == null) {
                throw failure
            }
            failure
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            val mapped = networkFailure(failure)
            if (beaconResolver == null) throw mapped
            mapped
        }

        // 当配置的局域网地址或旧公网隧道地址不可达时，自动通过配对码解密最新跨网长连接信标
        val beacon = runCatching { beaconResolver?.invoke(baseConfig.pairingToken) }.getOrNull()
        val candidates = buildList {
            if (beacon != null) {
                add(beacon.tunnelUrl)
                if (!beacon.lanUrl.isNullOrBlank()) add(beacon.lanUrl)
            }
            cachedDiscoveredEndpoint?.let { add(it) }
        }.map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() && it != baseConfig.endpoint }
            .distinct()

        for (candidateEndpoint in candidates) {
            val candidateConfig = runCatching {
                baseConfig.copy(endpoint = candidateEndpoint).validated()
            }.getOrNull() ?: continue
            try {
                val status = checkConnection(candidateConfig)
                cachedDiscoveredEndpoint = candidateConfig.endpoint
                lastResolvedEndpoint = candidateConfig.endpoint
                runCatching { onEndpointDiscovered?.invoke(candidateConfig.endpoint) }
                return candidateConfig to status
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 继续尝试下一个候选地址
            }
        }
        throw primaryFailure
    }

    private fun shouldAttemptRemoteDiscovery(failure: GatewayException): Boolean =
        failure.code in setOf("connection_failed", "connection_timeout", "http_error", "protocol_mismatch")


    private suspend fun checkConnection(config: GatewayConnectionConfig): GatewayConnectionStatus {
        val health = getJson(config, "/healthz", authenticated = false)
        if (!health.optBoolean("ok") || health.optString("protocol") != PROTOCOL) {
            throw GatewayException("protocol_mismatch", "该地址不是 EVA-X WorkBuddy 官方桥接，请检查电脑桥接地址（默认端口 3099）。")
        }
        val json = getJson(config, "/api/connection", authenticated = true)
        if (json.optString("provider") != "workbuddy") {
            throw GatewayException("protocol_mismatch", "连接状态不是 WorkBuddy，无法提交真实电脑任务。")
        }
        val reportedTunnelUrl = json.optString("tunnelUrl").trim().trimEnd('/')
        if (reportedTunnelUrl.startsWith("https://")) {
            cachedDiscoveredEndpoint = reportedTunnelUrl
        }
        val configured = json.optBoolean("configured")
        val authorized = json.optBoolean("authorized")
        val online = json.optBoolean("online")
        val reportedState = json.optString("state")
        val code = when {
            !configured -> "unconfigured"
            !authorized -> "unauthorized"
            !online -> "offline"
            reportedState == "busy" -> "busy"
            reportedState == "attention" -> "NEEDS_ATTENTION"
            reportedState != "online" -> "connection_failed"
            else -> null
        }
        return GatewayConnectionStatus(
            state = if (code == null) GatewayConnectionState.READY else GatewayConnectionState.ERROR,
            message = if (code == null) "WorkBuddy 已授权且在线，可以提交指令" else errorForCode(code, json.optString("message"), config).userMessage,
            configured = configured,
            authorized = authorized,
            online = online,
            checkedAt = System.currentTimeMillis(),
            errorCode = code,
        )
    }

    private suspend fun getJson(config: GatewayConnectionConfig, path: String, authenticated: Boolean): JSONObject {
        try {
            return withConnection(config, path, authenticated) { connection ->
                connection.requestMethod = "GET"
                connection.readTimeout = if (path == "/api/connection") 35_000 else 3_000
                connection.setRequestProperty("Accept", "application/json")
                requireSuccessfulHttp(connection, config)
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                try {
                    JSONObject(text)
                } catch (_: Exception) {
                    throw GatewayException("protocol_mismatch", "电脑桥接返回了无效的连接信息，请检查桥接版本。")
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: GatewayException) {
            throw failure
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            throw networkFailure(failure)
        }
    }

    private suspend fun streamResponse(
        config: GatewayConnectionConfig,
        prompt: String,
        onEvent: suspend (AssistantEvent) -> Unit,
    ) {
        var receivedOutput = false
        var receivedCompleted = false
        var requestMayHaveReachedBridge = false
        val requestId = UUID.randomUUID().toString()
        onEvent(AssistantEvent.RequestStarted(requestId))
        try {
            withConnection(config, "/api/evax/stream", authenticated = true) { connection ->
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.readTimeout = 25_000
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "text/event-stream")
                val payload = JSONObject().apply {
                    put("prompt", prompt)
                    put("requestId", requestId)
                }.toString()
                connection.outputStream.use {
                    requestMayHaveReachedBridge = true
                    it.write(payload.toByteArray(Charsets.UTF_8))
                }
                requireSuccessfulHttp(connection, config)
                if (!connection.contentType.orEmpty().lowercase().startsWith("text/event-stream")) {
                    throw GatewayException("unconfirmed_response", "电脑桥接没有返回指令事件流，无法确认指令结果。请先查看电脑端状态，不要重复提交。")
                }
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        currentCoroutineContext().ensureActive()
                        val trimmed = line.trim()
                        // 空行和 SSE 注释心跳不代表任务结果。
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data.isEmpty() || data == "[DONE]") continue
                        val json = try {
                            JSONObject(data)
                        } catch (_: Exception) {
                            throw GatewayException("invalid_event", "电脑桥接返回了无效的指令事件，未确认执行结果。")
                        }
                        val eventRequestId = nullableString(json, "requestId")
                        if (eventRequestId != null && eventRequestId != requestId) {
                            throw GatewayException("invalid_event", "电脑事件与当前请求不匹配，请先在电脑端确认状态。")
                        }
                        when (json.optString("type")) {
                            "progress" -> {
                                receivedOutput = true
                                onEvent(AssistantEvent.Progress(
                                    json.optString("step", "处理中…"), json.optInt("index"), json.optInt("total"),
                                    taskId = nullableString(json, "taskId"), requestId = eventRequestId,
                                ))
                            }
                            "delta" -> {
                                receivedOutput = true
                                onEvent(AssistantEvent.StreamDelta(json.optString("delta"), json.optString("partialText")))
                            }
                            "sentence" -> {
                                val sentence = json.optString("sentence").trim()
                                if (sentence.isNotEmpty()) {
                                    receivedOutput = true
                                    onEvent(AssistantEvent.SpeakSentence(sentence, json.optBoolean("isFirst"), json.optLong("latencyMs")))
                                }
                            }
                            "error" -> {
                                val mapped = errorForCode(json.optString("code", "task_failed"), json.optString("message"), config)
                                throw GatewayException(
                                    mapped.code, mapped.userMessage,
                                    outcome = parseOutcome(json), taskId = nullableString(json, "taskId"),
                                    requestId = eventRequestId, evidence = parseEvidence(json),
                                )
                            }
                            "completed" -> {
                                val text = json.optString("text").trim()
                                val outcome = parseOutcome(json)
                                if (text.isEmpty() || json.optBoolean("isSample", false) || outcome == null) {
                                    throw GatewayException("invalid_result", "电脑桥接未返回有效的 WorkBuddy 回复，未确认执行结果。")
                                }
                                receivedCompleted = true
                                val sampleLabel = nullableString(json, "sampleLabel") ?: "WorkBuddy 官方回复"
                                onEvent(AssistantEvent.SourceChanged(AssistantSource.PC_GATEWAY, sampleLabel))
                                // 流结束仅代表桥接响应结束；任务成功由结构化证据和当前任务标识共同确认。
                                onEvent(AssistantEvent.Completed(AssistantResult(
                                    text = text,
                                    sampleLabel = sampleLabel,
                                    followUps = followUps(json.optJSONArray("followUps")),
                                    isSample = false,
                                    source = AssistantSource.PC_GATEWAY,
                                    outcome = outcome,
                                    taskId = nullableString(json, "taskId"),
                                    requestId = eventRequestId,
                                    evidence = parseEvidence(json),
                                )))
                                break
                            }
                        }
                    }
                }
            }
            if (!receivedCompleted) {
                throw GatewayException("stream_interrupted", "电脑连接已中断，未收到 WorkBuddy 最终回复。任务可能仍在电脑端运行，请先查看电脑端状态。")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: GatewayException) {
            throw failure
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            if (receivedOutput) {
                throw GatewayException("stream_interrupted", "电脑连接已中断，未收到 WorkBuddy 最终回复。任务可能仍在电脑端运行，请先查看电脑端状态。", failure)
            }
            if (requestMayHaveReachedBridge) {
                throw GatewayException("SUBMISSION_UNKNOWN", "指令已经开始发送，但未确认电脑端是否收到。请先在电脑端确认状态，不要重复提交。", failure)
            }
            throw networkFailure(failure)
        }
    }

    private fun nullableString(json: JSONObject, key: String): String? =
        (json.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseOutcome(json: JSONObject): AssistantOutcome? = when (nullableString(json, "outcome")) {
        "reply_received" -> AssistantOutcome.REPLY_RECEIVED
        "task_success" -> AssistantOutcome.TASK_SUCCEEDED
        "task_pending" -> AssistantOutcome.NEEDS_ATTENTION
        "task_failed" -> AssistantOutcome.FAILED
        "task_uncertain" -> AssistantOutcome.UNCERTAIN
        "task_cancelled" -> AssistantOutcome.CANCELLED
        null -> if (json.optString("taskState") == "reply_received") AssistantOutcome.REPLY_RECEIVED else null
        else -> null
    }

    private fun parseEvidence(json: JSONObject): ExecutionEvidence? = json.optJSONObject("evidence")?.let {
        ExecutionEvidence(it.optString("source"), it.optString("status"), it.optBoolean("verified"), it.optBoolean("terminal"))
    }

    private suspend fun <T> withConnection(
        config: GatewayConnectionConfig,
        path: String,
        authenticated: Boolean,
        block: suspend (HttpURLConnection) -> T,
    ): T = coroutineScope {
        currentCoroutineContext().ensureActive()
        val connection = (URL(config.endpoint + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 5_000
            useCaches = false
            instanceFollowRedirects = false
            if (authenticated) setRequestProperty("Authorization", "Bearer ${config.pairingToken}")
        }
        val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            block(connection)
        } finally {
            closer.cancel()
            connection.disconnect()
        }
    }

    private fun requireSuccessfulHttp(connection: HttpURLConnection, config: GatewayConnectionConfig) {
        val status = connection.responseCode
        if (status == 200) return
        if (status in 300..399) {
            throw GatewayException("redirect_rejected", "电脑桥接地址发生了重定向，请填写最终桥接地址后重试。")
        }
        if (status == 404) throw GatewayException("protocol_mismatch", "该地址没有 WorkBuddy 桥接接口，请检查桥接地址（默认端口 3099）。")
        val error = runCatching {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { JSONObject(it.readText()) }
        }.getOrNull()
        if (error != null && error.optString("code").isNotBlank()) {
            val mapped = errorForCode(error.optString("code"), error.optString("message"), config)
            throw GatewayException(
                mapped.code, mapped.userMessage,
                outcome = parseOutcome(error), taskId = nullableString(error, "taskId"),
                requestId = nullableString(error, "requestId"), evidence = parseEvidence(error),
            )
        }
        if (connection.requestMethod == "POST" && status >= 500) {
            throw GatewayException("SUBMISSION_UNKNOWN", "指令发送后电脑桥接返回 HTTP $status，无法确认电脑端是否接收。请先查看电脑端状态，不要重复提交。")
        }
        if (status == 401 || status == 403) {
            throw GatewayException("pairing_failed", "电脑桥接拒绝访问，请检查配对码和电脑端授权状态。")
        }
        throw GatewayException("http_error", "电脑桥接返回 HTTP $status，指令未得到确认，请检查电脑端桥接状态。")
    }

    private fun errorForCode(code: String, message: String, config: GatewayConnectionConfig): GatewayException {
        val fallback = when (code.lowercase()) {
            "unconfigured", "not_configured", "workbuddy_unconfigured" -> "电脑桥接尚未配置 WorkBuddy 官方应用，请先完成电脑端配置。"
            "unauthorized", "authorization_required", "workbuddy_unauthorized", "auth_required" -> "WorkBuddy 尚未授权或授权已失效，请在电脑端完成官方应用授权。"
            "workbuddy_not_ready" -> "电脑桥接尚未完成 WorkBuddy 官方应用配置或授权，请先处理电脑端配置。"
            "scope_required" -> "WorkBuddy 官方应用授权权限不足，请在电脑端补充授权。"
            "offline", "workbuddy_offline" -> "WorkBuddy 桌面助理当前离线，请打开电脑端 WorkBuddy 并连接助理。"
            "busy", "workbuddy_busy" -> "WorkBuddy 正在处理另一项指令，请等待当前任务结束后再试。"
            "timeout", "upstream_timeout", "reply_timeout" -> "等待 WorkBuddy 回复超时，任务可能仍在执行，请先查看电脑端状态。"
            "submission_unknown", "needs_attention", "ambiguous_reply" -> "电脑端指令结果尚不明确，请先在电脑端确认状态，不要重复提交。"
            "duplicate_request" -> "该请求已经提交，请先查看电脑端原任务的状态。"
            "pairing_failed", "pairing_required" -> "配对码无效，请重新填写电脑端配对码。"
            "origin_denied" -> "电脑桥接拒绝了请求来源，请检查电脑端桥接访问设置。"
            "upstream_unavailable", "upstream_error" -> "WorkBuddy 官方服务暂时不可用，指令未得到确认。"
            else -> "WorkBuddy 未确认本次指令，请查看电脑端状态后重试。"
        }
        val safeMessage = message.trim().replace(config.pairingToken, "[配对码已隐藏]").take(400)
        return GatewayException(code, safeMessage.ifBlank { fallback })
    }

    private fun networkFailure(failure: Exception): GatewayException = if (failure is SocketTimeoutException) {
        GatewayException("connection_timeout", "连接电脑桥接超时，请检查地址、网络和电脑端桥接是否运行。", failure)
    } else {
        GatewayException("connection_failed", "无法连接电脑桥接，请检查手机与电脑的网络、桥接地址和端口。", failure)
    }

    private fun followUps(array: JSONArray?): List<String> = buildList {
        if (array != null) for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }

    companion object {
        const val PROTOCOL = "evax-workbuddy-v1"
    }
}
