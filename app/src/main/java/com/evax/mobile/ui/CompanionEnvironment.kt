package com.evax.mobile.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberCompanionForeground(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return foreground
}

@Composable
internal fun rememberCompanionPowerSaving(): Boolean {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var lowBattery by remember { mutableStateOf(false) }
    var powerSaving by remember(manager) { mutableStateOf(manager?.isPowerSaveMode == true) }
    DisposableEffect(context, manager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSaving = manager?.isPowerSaveMode == true
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    lowBattery = !plugged && level >= 0 && scale > 0 && level * 100L / scale <= 15L
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return powerSaving || lowBattery
}
