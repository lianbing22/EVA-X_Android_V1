package com.evax.mobile.domain

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class GatewayTunnelBeacon(
    val tunnelUrl: String,
    val lanUrl: String? = null,
    val updatedAt: String? = null,
)

/**
 * 方案一：基于配对码 SHA-256 派生的 AES-256-GCM 跨网长连接隧道自动寻址器。
 * 当手机切到 5G 外网或电脑更换 IP / 重启生成新隧道地址时，仅凭配对码即可安全解密出最新的公网长连接地址。
 */
object TunnelBeaconResolver {
    const val DEFAULT_RELAY_ORIGIN = "https://ntfy.sh"

    fun deriveDiscoveryTopic(pairingToken: String): String {
        val digest = sha256("evax-discovery-v1:${pairingToken.trim()}")
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "evax-wb-${hex.take(28)}"
    }

    fun deriveDiscoveryKey(pairingToken: String): ByteArray =
        sha256("evax-cipher-v1:${pairingToken.trim()}")

    fun encryptTunnelBeacon(
        pairingToken: String,
        tunnelUrl: String,
        lanUrl: String? = null,
        updatedAt: String? = null,
    ): String {
        val key = SecretKeySpec(deriveDiscoveryKey(pairingToken), "AES")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val payload = buildString {
            append("""{"tunnelUrl":"""").append(escapeJson(tunnelUrl)).append('"')
            if (!lanUrl.isNullOrBlank()) {
                append(""","lanUrl":"""").append(escapeJson(lanUrl)).append('"')
            }
            if (!updatedAt.isNullOrBlank()) {
                append(""","updatedAt":"""").append(escapeJson(updatedAt)).append('"')
            }
            append('}')
        }
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val dataB64 = Base64.getEncoder().encodeToString(encrypted)
        return """{"v":1,"iv":"$ivB64","data":"$dataB64"}"""
    }

    fun decryptTunnelBeacon(pairingToken: String, envelopeJson: String): GatewayTunnelBeacon? {
        return try {
            val version = Regex(""""v"\s*:\s*(\d+)""").find(envelopeJson)?.groupValues?.get(1)?.toIntOrNull()
            if (version != 1) return null
            val ivB64 = extractJsonString(envelopeJson, "iv") ?: return null
            val dataB64 = extractJsonString(envelopeJson, "data") ?: return null
            val iv = Base64.getDecoder().decode(ivB64)
            val combined = Base64.getDecoder().decode(dataB64)
            if (iv.size != 12 || combined.size < 17) return null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = SecretKeySpec(deriveDiscoveryKey(pairingToken), "AES")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val decrypted = String(cipher.doFinal(combined), Charsets.UTF_8)

            val tunnelUrl = extractJsonString(decrypted, "tunnelUrl")?.trim()?.trimEnd('/') ?: return null
            if (!tunnelUrl.startsWith("https://") && !tunnelUrl.startsWith("http://")) return null
            GatewayConnectionConfig(tunnelUrl, pairingToken).validated()
            val lanUrl = extractJsonString(decrypted, "lanUrl")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            val updatedAt = extractJsonString(decrypted, "updatedAt")?.trim()?.takeIf { it.isNotEmpty() }
            GatewayTunnelBeacon(
                tunnelUrl = tunnelUrl,
                lanUrl = lanUrl,
                updatedAt = updatedAt,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun resolve(
        pairingToken: String,
        relayOrigin: String = DEFAULT_RELAY_ORIGIN,
    ): GatewayTunnelBeacon? {
        val cleanToken = pairingToken.trim()
        if (cleanToken.isEmpty()) return null
        val topic = deriveDiscoveryTopic(cleanToken)
        val url = "${relayOrigin.trimEnd('/')}/$topic/json?poll=1&since=all"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4_000
            readTimeout = 5_000
            useCaches = false
            setRequestProperty("Accept", "application/x-ndjson, text/plain")
        }
        return try {
            if (connection.responseCode != 200) return null
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList().asReversed()
            for (line in lines) {
                val eventType = extractJsonString(line, "event")
                if (eventType != null && eventType != "message") continue
                val messageRaw = extractJsonString(line, "message") ?: line
                val beacon = decryptTunnelBeacon(cleanToken, messageRaw)
                if (beacon != null) return beacon
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = pattern.find(json)?.groupValues?.get(1) ?: return null
        return buildString(raw.length) {
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    when (val next = raw[i + 1]) {
                        '"', '\\', '/' -> { append(next); i += 2 }
                        'n' -> { append('\n'); i += 2 }
                        'r' -> { append('\r'); i += 2 }
                        't' -> { append('\t'); i += 2 }
                        'u' -> {
                            if (i + 5 < raw.length) {
                                val code = raw.substring(i + 2, i + 6).toIntOrNull(16)
                                if (code != null) {
                                    append(code.toChar())
                                    i += 6
                                    continue
                                }
                            }
                            append(next)
                            i += 2
                        }
                        else -> { append(next); i += 2 }
                    }
                } else {
                    append(c)
                    i += 1
                }
            }
        }
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
