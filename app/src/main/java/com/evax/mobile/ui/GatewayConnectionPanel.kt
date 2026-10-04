package com.evax.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.evax.mobile.domain.GatewayConnectionConfig
import com.evax.mobile.domain.GatewayConnectionState
import com.evax.mobile.domain.GatewayConnectionStatus
import java.net.URI

/** A connection test checks pairing and assistant availability; it never submits office work. */
@Composable
internal fun GatewayConnectionPanel(
    config: GatewayConnectionConfig,
    status: GatewayConnectionStatus,
    isProcessing: Boolean,
    onSave: (GatewayConnectionConfig) -> Unit,
    onTest: (GatewayConnectionConfig) -> Unit,
    onBack: () -> Unit,
    hasUnverifiedEdits: Boolean = false,
    verifiedInput: GatewayConnectionConfig? = null,
    onInputEdited: () -> Unit = {},
) {
    var endpoint by remember(config) { mutableStateOf(config.endpoint) }
    var pairingToken by remember(config) { mutableStateOf(config.pairingToken) }
    var revealToken by remember { mutableStateOf(false) }
    var validationInvalidated by remember(config) { mutableStateOf(false) }
    var awaitingResult by remember(config) { mutableStateOf(false) }
    var sawChecking by remember(config) { mutableStateOf(false) }
    var previousCheckTime by remember(config) { mutableStateOf<Long?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val draft = GatewayConnectionConfig(endpoint.trim(), pairingToken.trim())
    val unsaved = draft != config
    val endpointError = endpointValidationError(endpoint)
    val canAct = !isProcessing && !status.isTesting && !awaitingResult && endpointError == null && draft.pairingToken.isNotEmpty()

    LaunchedEffect(status.state, status.checkedAt, status.message) {
        if (awaitingResult) {
            if (status.isTesting) sawChecking = true
            else if (sawChecking || status.checkedAt != previousCheckTime) awaitingResult = false
        }
    }
    val checking = status.isTesting || awaitingResult
    val effectiveStatus = when {
        checking -> GatewayConnectionStatus(state = GatewayConnectionState.CHECKING, message = if (status.isTesting) status.message else "正在确认配对和 WorkBuddy 可用性")
        validationInvalidated || hasUnverifiedEdits || verifiedInput?.let { it != draft } == true -> GatewayConnectionStatus(
            state = if (draft.endpoint.isBlank() || draft.pairingToken.isBlank()) GatewayConnectionState.UNCONFIGURED else GatewayConnectionState.NOT_CHECKED,
            message = "输入已改变，请重新测试连接",
            configured = draft.endpoint.isNotBlank() && draft.pairingToken.isNotBlank(),
        )
        else -> status
    }
    fun inputEdited() {
        validationInvalidated = true
        awaitingResult = false
        sawChecking = false
        onInputEdited()
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("连接电脑上的 WorkBuddy", style = MaterialTheme.typography.titleMedium)
        Text(
            "填写电脑桥接显示的地址和配对码。电脑与手机需能互相访问。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("gateway_status"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, if (effectiveStatus.state == GatewayConnectionState.READY) Color(0x5545E5CC) else MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        gatewayConnectionStateTitle(effectiveStatus),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (effectiveStatus.state == GatewayConnectionState.READY) Color(0xFF45E5CC) else MaterialTheme.colorScheme.onSurface,
                    )
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Text(effectiveStatus.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (effectiveStatus.state == GatewayConnectionState.READY && unsaved) {
                    Text("当前输入已验证，保存后生效", color = Color(0xFF45E5CC), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedTextField(
            value = endpoint,
            onValueChange = { if (endpoint != it) { endpoint = it; inputEdited() } },
            enabled = !isProcessing && !checking,
            modifier = Modifier.fillMaxWidth().testTag("gateway_endpoint"),
            label = { Text("电脑桥接地址") },
            placeholder = { Text("http://192.168.1.100:3099") },
            singleLine = true,
            isError = endpoint.isNotBlank() && endpointError != null,
            supportingText = { Text(endpointError ?: "例如 http://192.168.1.100:3099") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
        )
        OutlinedTextField(
            value = pairingToken,
            onValueChange = { if (pairingToken != it) { pairingToken = it; inputEdited() } },
            enabled = !isProcessing && !checking,
            modifier = Modifier.fillMaxWidth().testTag("gateway_pairing_token"),
            label = { Text("配对码") },
            singleLine = true,
            visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { revealToken = !revealToken }, enabled = !isProcessing && !checking) {
                    Text(if (revealToken) "隐藏" else "显示")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        )
        if (isProcessing) {
            Text("任务执行中，完成或停止接收后可修改连接", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else if (unsaved) {
            Text("更改尚未保存", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { keyboard?.hide(); onSave(draft) },
                enabled = canAct,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("gateway_save"),
            ) { Text("保存连接") }
            OutlinedButton(
                onClick = {
                    keyboard?.hide()
                    validationInvalidated = false
                    awaitingResult = true
                    sawChecking = false
                    previousCheckTime = status.checkedAt
                    onTest(draft)
                },
                enabled = canAct,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("gateway_test"),
            ) { Text(if (checking) "检查中…" else "测试连接") }
        }
        Text("连接测试不会执行办公任务。实际任务结果请查看对话中的电脑回复。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onBack) { Text("返回设置") }
    }
}

internal fun gatewayConnectionStateTitle(status: GatewayConnectionStatus): String = when (status.state) {
    GatewayConnectionState.UNCONFIGURED -> "未配置"
    GatewayConnectionState.NOT_CHECKED -> "未验证"
    GatewayConnectionState.CHECKING -> "正在检查连接"
    GatewayConnectionState.READY -> if (status.authorized && status.online) "在线" else "连接尚未确认"
    GatewayConnectionState.ERROR -> {
        val code = status.errorCode.orEmpty().lowercase()
        when {
            code.contains("unauthor") || code.contains("pairing") || code.contains("auth") || code == "401" || code == "403" -> "未授权"
            status.authorized && !status.online || code.contains("offline") -> "助理离线"
            code == "unconfigured" || code == "not_configured" -> "未配置"
            code == "busy" -> "助理忙碌"
            code == "needs_attention" -> "需要电脑端处理"
            else -> "连接失败"
        }
    }
}

private fun endpointValidationError(endpoint: String): String? {
    if (endpoint.isBlank()) return "请输入电脑桥接地址"
    val uri = runCatching { URI(endpoint.trim()) }.getOrNull()
    if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() ||
        uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
        uri.port !in -1..65535 || uri.port == 0 || uri.rawPath.orEmpty().contains("..")) {
        return "请输入完整的 http:// 或 https:// 地址"
    }
    if (uri.port == 3088) return "请使用 WorkBuddy 桥接地址，默认端口为 3099"
    return null
}
