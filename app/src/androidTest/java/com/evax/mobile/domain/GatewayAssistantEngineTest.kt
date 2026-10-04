package com.evax.mobile.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GatewayAssistantEngineTest {
    @Test
    fun successfulGatewayPreservesRealSourceAndProgressWithoutSampleFlag() {
        TestGateway().use { server ->
            val events = collect(server.config)
            val progress = events.filterIsInstance<AssistantEvent.Progress>().single()
            val result = events.filterIsInstance<AssistantEvent.Completed>().single().result
            assertEquals(AssistantSource.PC_GATEWAY, events.filterIsInstance<AssistantEvent.SourceChanged>().single().source)
            assertEquals(2, progress.index)
            assertEquals(5, progress.total)
            assertEquals("官方助理回复", result.text)
            assertEquals("WorkBuddy 官方回复", result.sampleLabel)
            assertFalse(result.isSample)
            assertEquals(AssistantSource.PC_GATEWAY, result.source)
            assertEquals(listOf("/healthz", "/api/connection", "/api/evax/stream"), server.requests.map { it.path })
            assertNull(server.requests[0].headers["authorization"])
            assertEquals("Bearer test-pairing-token", server.requests[1].headers["authorization"])
            assertEquals("Bearer test-pairing-token", server.requests[2].headers["authorization"])
            val body = JSONObject(server.requests[2].body)
            assertEquals("test", body.getString("prompt"))
            UUID.fromString(body.getString("requestId"))
        }
    }

    @Test
    fun missingConfigurationFailsWithoutDemo() {
        assertEquals("not_configured", failure { collect(GatewayConnectionConfig()) }.code)
    }

    @Test
    fun unavailableGatewayReportsFailureWithoutDemo() {
        TestGateway(healthStatus = 503, expectedRequests = 1).use { server ->
            assertEquals("http_error", failure { collect(server.config) }.code)
            assertEquals(listOf("/healthz"), server.requests.map { it.path })
        }
    }

    @Test
    fun oldGatewayWithHttp200IsRejectedByProtocol() {
        TestGateway(healthBody = "{}", expectedRequests = 1).use { server ->
            assertEquals("protocol_mismatch", failure { collect(server.config) }.code)
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun connectionTestDoesNotSubmitTask() {
        TestGateway(expectedRequests = 2).use { server ->
            val checked = runBlocking { GatewayAssistantEngine { server.config }.testConnection(server.config) }
            assertEquals(GatewayConnectionState.READY, checked.state)
            assertTrue(checked.configured && checked.authorized && checked.online)
            assertNotNull(checked.checkedAt)
            assertEquals(listOf("GET", "GET"), server.requests.map { it.method })
            assertEquals(listOf("/healthz", "/api/connection"), server.requests.map { it.path })
        }
    }

    @Test
    fun invalidPairingCodeIsDistinctFromWorkBuddyAuthorization() {
        TestGateway(
            connectionStatus = 401,
            connectionBody = """{"code":"PAIRING_REQUIRED","message":"配对码无效"}""",
            expectedRequests = 2,
        ).use { server ->
            assertEquals("PAIRING_REQUIRED", failure { collect(server.config) }.code)
        }
        TestGateway(
            streamStatus = 401,
            streamBody = """{"code":"AUTH_REQUIRED","message":"官方授权已失效"}""",
        ).use { server ->
            val error = failure { collect(server.config) }
            assertEquals("AUTH_REQUIRED", error.code)
            assertEquals("官方授权已失效", error.userMessage)
        }
    }

    @Test
    fun onlineAssistantNeedingAttentionDoesNotAcceptNewTask() {
        TestGateway(
            connectionBody = """{"provider":"workbuddy","configured":true,"authorized":true,"online":true,"state":"attention","message":"请先在电脑端确认上一条指令"}""",
            expectedRequests = 2,
        ).use { server ->
            assertEquals("NEEDS_ATTENTION", failure { collect(server.config) }.code)
            assertEquals(2, server.requests.size)
        }
    }

    @Test
    fun configurationAuthorizationAndOnlineFailuresPreventSubmission() {
        for ((configured, authorized, online, expectedCode) in listOf(
            listOf(false, false, false, "unconfigured"),
            listOf(true, false, false, "unauthorized"),
            listOf(true, true, false, "offline"),
        )) {
            TestGateway(
                connectionBody = """{"provider":"workbuddy","configured":$configured,"authorized":$authorized,"online":$online,"state":"offline"}""",
                expectedRequests = 2,
            ).use { server ->
                assertEquals(expectedCode, failure { collect(server.config) }.code)
                assertEquals(2, server.requests.size)
            }
        }
    }

    @Test
    fun serverErrorDoesNotFabricateCompletedResultOrLeakPairingToken() {
        TestGateway(streamBody = "data: {\"type\":\"error\",\"code\":\"SUBMISSION_UNKNOWN\",\"message\":\"请勿重试 test-pairing-token\"}\n\n").use { server ->
            val error = failure { collect(server.config) }
            assertEquals("SUBMISSION_UNKNOWN", error.code)
            assertFalse(error.userMessage.contains(server.config.pairingToken))
            assertTrue(error.userMessage.contains("请勿重试"))
        }
    }

    @Test
    fun disconnectAfterProgressDoesNotFabricateDemoCompletion() {
        TestGateway(streamBody = "data: {\"type\":\"progress\",\"step\":\"等待电脑回复\",\"index\":1,\"total\":2}\n\n").use { server ->
            assertEquals("stream_interrupted", failure { collect(server.config) }.code)
        }
    }

    @Test
    fun completedMustBeOfficialReplyReceivedNotSampleOrBlank() {
        for (event in listOf(
            """{"type":"completed","text":"演示结果","taskState":"reply_received","isSample":true}""",
            """{"type":"completed","text":"假完成"}""",
            """{"type":"completed","text":"","taskState":"reply_received"}""",
        )) {
            TestGateway(streamBody = "data: $event\n\n").use { server ->
                val error = failure { collect(server.config) }
                assertEquals("invalid_result", error.code)
                assertFalse(error.canRetryTask)
            }
        }
    }

    @Test
    fun malformedEventWrongContentTypeAndUnknownHttpFailureAfterPostBlockRetry() {
        TestGateway(streamBody = "data: invalid-json\n\n").use { server ->
            val error = failure { collect(server.config) }
            assertEquals("invalid_event", error.code)
            assertFalse(error.canRetryTask)
        }
        TestGateway(streamContentType = "application/json", streamBody = "{}").use { server ->
            val error = failure { collect(server.config) }
            assertEquals("unconfirmed_response", error.code)
            assertFalse(error.canRetryTask)
        }
        TestGateway(streamStatus = 502, streamBody = "upstream disconnected").use { server ->
            val error = failure { collect(server.config) }
            assertEquals("SUBMISSION_UNKNOWN", error.code)
            assertFalse(error.canRetryTask)
        }
    }

    @Test
    fun configurationSnapshotIsStableDuringRequestAndUpdatesNextRequest() {
        val current = AtomicReference(GatewayConnectionConfig())
        TestGateway(expectedRequests = 6, onRequest = { request ->
            if (request.path == "/healthz") current.set(current.get().copy(pairingToken = "second-token"))
        }).use { server ->
            current.set(server.config)
            val engine = GatewayAssistantEngine { current.get() }
            runBlocking { withTimeout(5_000) { engine.respond("first").toList() } }
            runBlocking { withTimeout(5_000) { engine.respond("second").toList() } }
            assertEquals("Bearer test-pairing-token", server.requests[1].headers["authorization"])
            assertEquals("Bearer test-pairing-token", server.requests[2].headers["authorization"])
            assertEquals("Bearer second-token", server.requests[4].headers["authorization"])
            assertEquals("Bearer second-token", server.requests[5].headers["authorization"])
            assertNotEquals(JSONObject(server.requests[2].body).getString("requestId"), JSONObject(server.requests[5].body).getString("requestId"))
        }
    }

    @Test
    fun authenticationRedirectIsNotFollowed() {
        TestGateway(connectionStatus = 302, expectedRequests = 2).use { server ->
            assertEquals("redirect_rejected", failure { collect(server.config) }.code)
            assertEquals(listOf("/healthz", "/api/connection"), server.requests.map { it.path })
        }
    }

    @Test
    fun missingProgressNumbersRemainUnknownAndHeartbeatIsIgnored() {
        TestGateway(streamBody = ": keepalive\n\ndata: {\"type\":\"progress\",\"step\":\"正在准备\"}\n\ndata: {\"type\":\"completed\",\"text\":\"已收到官方回复\",\"taskState\":\"reply_received\"}\n\n").use { server ->
            val progress = collect(server.config).filterIsInstance<AssistantEvent.Progress>().single()
            assertEquals(0, progress.index)
            assertEquals(0, progress.total)
        }
    }

    private fun collect(config: GatewayConnectionConfig): List<AssistantEvent> = runBlocking {
        withTimeout(5_000L) { GatewayAssistantEngine { config }.respond("test").toList() }
    }

    private fun failure(block: () -> Unit): GatewayException {
        try {
            block()
        } catch (failure: GatewayException) {
            return failure
        }
        throw AssertionError("应报告真实网关失败，而不是返回演示结果")
    }
}

private data class GatewayRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String)

/** 仅绑定 Android 自身 loopback 的随机端口，不调用实际电脑或云端服务。 */
private class TestGateway(
    private val healthStatus: Int = 200,
    private val healthBody: String = """{"ok":true,"protocol":"evax-workbuddy-v1"}""",
    private val connectionStatus: Int = 200,
    private val connectionBody: String = """{"provider":"workbuddy","configured":true,"authorized":true,"online":true,"state":"online","message":"助理在线"}""",
    private val streamStatus: Int = 200,
    private val streamContentType: String = "text/event-stream",
    private val streamBody: String = "data: {\"type\":\"progress\",\"step\":\"等待电脑回复\",\"index\":2,\"total\":5}\n\ndata: {\"type\":\"completed\",\"text\":\"官方助理回复\",\"taskState\":\"reply_received\"}\n\n",
    private val expectedRequests: Int = 3,
    private val onRequest: (GatewayRequest) -> Unit = {},
) : Closeable {
    private val listener = ServerSocket().apply {
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        soTimeout = 5_000
    }
    val config = GatewayConnectionConfig("http://127.0.0.1:${listener.localPort}", "test-pairing-token")
    val requests: MutableList<GatewayRequest> = Collections.synchronizedList(mutableListOf())
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var serverFailure: Throwable? = null
    private val worker = Thread({
        try {
            repeat(expectedRequests) {
                listener.accept().use { socket ->
                    activeSocket = socket
                    socket.soTimeout = 5_000
                    respond(socket)
                    activeSocket = null
                }
            }
        } catch (failure: Exception) {
            if (!listener.isClosed) serverFailure = failure
        }
    }, "evax-official-bridge-protocol-test").apply { isDaemon = true; start() }

    private fun respond(socket: Socket) {
        val input = socket.getInputStream().buffered()
        val requestLine = readHttpLine(input) ?: error("请求行为空")
        val method = requestLine.split(' ')[0]
        val path = requestLine.split(' ')[1]
        val headers = mutableMapOf<String, String>()
        while (true) {
            val header = readHttpLine(input) ?: error("请求头未结束")
            if (header.isEmpty()) break
            headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
        }
        val bytes = ByteArray(headers["content-length"]?.toInt() ?: 0)
        for (index in bytes.indices) { val byte = input.read(); check(byte >= 0); bytes[index] = byte.toByte() }
        val request = GatewayRequest(method, path, headers, String(bytes, Charsets.UTF_8))
        requests += request
        onRequest(request)
        val (status, body) = when (path) {
            "/healthz" -> healthStatus to healthBody
            "/api/connection" -> connectionStatus to connectionBody
            "/api/evax/stream" -> streamStatus to streamBody
            else -> 404 to "{}"
        }
        val data = body.toByteArray(Charsets.UTF_8)
        val contentType = if (path == "/api/evax/stream" && status == 200) streamContentType else "application/json"
        val redirect = if (status == 302) "Location: /redirected\r\n" else ""
        val response = "HTTP/1.1 $status Response\r\nContent-Type: $contentType; charset=utf-8\r\nContent-Length: ${data.size}\r\n${redirect}Connection: close\r\n\r\n"
        socket.getOutputStream().apply { write(response.toByteArray(Charsets.US_ASCII)); write(data); flush() }
    }

    override fun close() {
        listener.close()
        activeSocket?.close()
        worker.join(1_000L)
        serverFailure?.let { throw AssertionError("loopback 网关测试服务失败", it) }
    }

    private fun readHttpLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString("US-ASCII")
            if (value == '\n'.code) return bytes.toString("US-ASCII").trimEnd('\r')
            bytes.write(value)
        }
    }
}
