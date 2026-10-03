package com.evax.mobile.domain

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject

/**
 * 电脑端 DeepSeek Harness (dsh-evax-bridge) & WorkBuddy 极速流式网关引擎。
 *
 * - 自动探测并缓存最快的电脑网关地址（ADB 映射 127.0.0.1:3088 -> 模拟器 10.0.2.2:3088 -> 局域网 Wi-Fi 192.168.2.4:3088）。
 * - 实时解析 SSE 事件流（Progress / StreamDelta / SpeakSentence / Completed），首句达到即触发 TTS 朗读。
 * - 若电脑网关离线，自动无感降级至本地 DemoAssistantEngine 并同样触发首句朗读。
 */
class GatewayAssistantEngine(
    private val fallbackEngine: AssistantEngine = DemoAssistantEngine(),
    private val candidateEndpoints: List<String> = listOf(
        "http://127.0.0.1:3088",
        "http://10.0.2.2:3088",
        "http://192.168.2.4:3088",
    ),
) : AssistantEngine {

    @Volatile
    private var preferredBaseUrl: String? = null

    override fun respond(prompt: String): Flow<AssistantEvent> = flow {
        val baseUrl = resolveActiveGateway()
        if (baseUrl != null) {
            val streamed = tryStreamFromGateway(baseUrl, prompt) { event ->
                emit(event)
            }
            if (streamed) return@flow
        }

        // 电脑网关不可达时，降级到本地引擎并自动补发首句朗读事件
        fallbackEngine.respond(prompt).collect { event ->
            if (event is AssistantEvent.Completed) {
                emit(
                    AssistantEvent.SpeakSentence(
                        sentence = event.result.text,
                        isFirst = true,
                        latencyMs = 15L,
                    )
                )
            }
            emit(event)
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveActiveGateway(): String? {
        val currentPreferred = preferredBaseUrl
        val ordered = if (currentPreferred != null) {
            listOf(currentPreferred) + candidateEndpoints.filterNot { it == currentPreferred }
        } else {
            candidateEndpoints
        }

        for (candidate in ordered) {
            if (probeHealth(candidate)) {
                preferredBaseUrl = candidate
                return candidate
            }
        }
        return null
    }

    private fun probeHealth(baseUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$baseUrl/healthz").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 550
                readTimeout = 550
                useCaches = false
            }
            conn.responseCode == 200
        } catch (_: Throwable) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun tryStreamFromGateway(
        baseUrl: String,
        prompt: String,
        onEvent: suspend (AssistantEvent) -> Unit,
    ): Boolean {
        var conn: HttpURLConnection? = null
        var receivedCompleted = false
        return try {
            conn = (URL("$baseUrl/api/evax/stream").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 1500
                readTimeout = 25000
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream")
            }

            val payload = JSONObject().apply {
                put("prompt", prompt)
            }.toString()

            conn.outputStream.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            if (conn.responseCode != 200) return false

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (!trimmed.startsWith("data:")) continue
                    val dataStr = trimmed.removePrefix("data:").trim()
                    if (dataStr == "[DONE]" || dataStr.isEmpty()) continue

                    val json = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
                    when (json.optString("type")) {
                        "progress" -> {
                            onEvent(
                                AssistantEvent.Progress(
                                    step = json.optString("step", "处理中…"),
                                    index = json.optInt("index", 1),
                                    total = json.optInt("total", 2),
                                )
                            )
                        }

                        "delta" -> {
                            onEvent(
                                AssistantEvent.StreamDelta(
                                    delta = json.optString("delta", ""),
                                    partialText = json.optString("partialText", ""),
                                )
                            )
                        }

                        "sentence" -> {
                            val sentence = json.optString("sentence", "").trim()
                            if (sentence.isNotEmpty()) {
                                onEvent(
                                    AssistantEvent.SpeakSentence(
                                        sentence = sentence,
                                        isFirst = json.optBoolean("isFirst", false),
                                        latencyMs = json.optLong("latencyMs", 0L),
                                    )
                                )
                            }
                        }

                        "completed" -> {
                            val text = json.optString("text", "").trim()
                            val sampleLabel = json.optString("sampleLabel", "PC · DSH+WorkBuddy")
                            val followUpsJson: JSONArray? = json.optJSONArray("followUps")
                            val followUps = buildList {
                                if (followUpsJson != null) {
                                    for (i in 0 until followUpsJson.length()) {
                                        val item = followUpsJson.optString(i).trim()
                                        if (item.isNotEmpty()) add(item)
                                    }
                                }
                            }
                            receivedCompleted = true
                            onEvent(
                                AssistantEvent.Completed(
                                    AssistantResult(
                                        text = text,
                                        sampleLabel = sampleLabel,
                                        followUps = followUps,
                                    )
                                )
                            )
                        }
                    }
                }
            }
            receivedCompleted
        } catch (_: Throwable) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
