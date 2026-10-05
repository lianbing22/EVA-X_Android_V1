package com.evax.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A single writer owns the pose. A cancelled generation cannot publish another frame. */
internal class AvatarBehaviorPlayer(private val scope: CoroutineScope) {
    var frame by mutableStateOf<AvatarBehaviorFrame?>(null)
        private set
    private var job: Job? = null
    private var generation = 0L
    private var priority = -1

    fun play(script: AvatarScript, scene: IdleScene? = null, requestedPriority: Int = 0): Job? {
        if (job?.isActive == true && priority > requestedPriority) return null
        cancel()
        val token = generation
        priority = requestedPriority
        frame = AvatarBehaviorFrame(script.sample(0), scene)
        return scope.launch {
            try {
                val start = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    if (generation != token) break
                    val elapsed = ((now - start) / 1_000_000L).coerceAtMost(script.durationMillis)
                    frame = AvatarBehaviorFrame(script.sample(elapsed), scene, elapsed.toFloat() / script.durationMillis)
                    if (elapsed >= script.durationMillis) break
                }
            } finally {
                if (generation == token) {
                    frame = null
                    priority = -1
                    job = null
                }
            }
        }.also { job = it }
    }

    fun cancelScene() {
        if (frame?.scene != null) cancel()
    }

    fun cancelRun(run: Job?) {
        if (run != null && job === run) cancel()
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        frame = null
        priority = -1
    }
}
