package com.evax.mobile.platform.gateway

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.evax.mobile.domain.GatewayConnectionConfig
import com.evax.mobile.domain.GatewayException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** 配对配置仅以 Android Keystore 加密后的密文保存在 app 私有目录。 */
class GatewayConfigStore(
    context: Context,
    preferencesName: String = "evax_gateway_connection",
    private val keyAlias: String = "evax_gateway_connection_v1",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): GatewayConnectionConfig {
        val stored = preferences.getString("encrypted_config", null) ?: return GatewayConnectionConfig()
        return try {
            val envelope = JSONObject(stored)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP))
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            GatewayConnectionConfig(json.getString("endpoint"), json.getString("pairingToken"))
        } catch (_: Exception) {
            throw GatewayException("storage_read_failed", "无法读取已保存的配对配置，请重新保存连接设置。")
        }
    }

    @Synchronized
    fun save(config: GatewayConnectionConfig) {
        val validConfig = config.validated()
        try {
            val plaintext = JSONObject().apply {
                put("endpoint", validConfig.endpoint)
                put("pairingToken", validConfig.pairingToken)
            }.toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
            val ciphertext = cipher.doFinal(plaintext)
            val envelope = JSONObject().apply {
                put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }.toString()
            if (!preferences.edit().putString("encrypted_config", envelope).commit()) {
                throw GatewayException("storage_write_failed", "连接配置保存失败，请重试。")
            }
        } catch (failure: GatewayException) {
            throw failure
        } catch (_: Exception) {
            throw GatewayException("storage_write_failed", "无法安全保存配对配置，请重试。")
        }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }
}
