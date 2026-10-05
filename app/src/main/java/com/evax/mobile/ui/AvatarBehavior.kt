package com.evax.mobile.ui

import com.evax.mobile.presentation.AvatarFeedbackKind
import com.evax.mobile.presentation.ListeningStage
import kotlin.random.Random

/** All distances are relative to the eye size; the layout anchor never moves. */
internal data class AvatarPose(
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val openness: Float = 1f,
    val width: Float = 1f,
    val scale: Float = 1f,
    val tilt: Float = 0f,
    val nod: Float = 0f,
    val smile: Float = 0f,
    val sadness: Float = 0f,
    val asymmetry: Float = 0f,
    val brightness: Float = 1f,
    val propAlpha: Float = 0f,
) {
    fun interpolate(other: AvatarPose, fraction: Float): AvatarPose {
        val t = fraction.coerceIn(0f, 1f)
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return AvatarPose(
            mix(gazeX, other.gazeX), mix(gazeY, other.gazeY), mix(openness, other.openness),
            mix(width, other.width), mix(scale, other.scale), mix(tilt, other.tilt),
            mix(nod, other.nod), mix(smile, other.smile), mix(sadness, other.sadness),
            mix(asymmetry, other.asymmetry), mix(brightness, other.brightness), mix(propAlpha, other.propAlpha),
        )
    }
}

internal data class AvatarKeyframe(val atMillis: Long, val pose: AvatarPose)

/** Short transitions followed by repeated poses create a fast glance and a real pause. */
internal class AvatarScript(val frames: List<AvatarKeyframe>) {
    init {
        require(frames.size >= 2)
        require(frames.first().atMillis == 0L)
        require(frames.zipWithNext().all { (a, b) -> b.atMillis > a.atMillis })
    }
    val durationMillis: Long = frames.last().atMillis

    fun sample(elapsedMillis: Long): AvatarPose {
        val time = elapsedMillis.coerceIn(0L, durationMillis)
        val right = frames.indexOfFirst { it.atMillis >= time }
        if (right <= 0) return frames.first().pose
        val start = frames[right - 1]
        val end = frames[right]
        val fraction = (time - start.atMillis).toFloat() / (end.atMillis - start.atMillis)
        val eased = fraction * fraction * (3f - 2f * fraction)
        return start.pose.interpolate(end.pose, eased)
    }
}

internal data class AvatarBehaviorFrame(
    val pose: AvatarPose,
    val scene: IdleScene? = null,
    val progress: Float = 0f,
)

internal fun avatarScript(vararg frames: Pair<Long, AvatarPose>) =
    AvatarScript(frames.map { AvatarKeyframe(it.first, it.second) })

internal fun listeningPose(stage: ListeningStage, measuredLevel: Float): AvatarPose {
    val level = measuredLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return when (stage) {
        ListeningStage.PREPARING -> AvatarPose(openness = 0.92f, scale = 1.01f)
        ListeningStage.READY -> AvatarPose(openness = 1.07f, tilt = 3.5f, scale = 1.025f)
        ListeningStage.SPEAKING -> AvatarPose(openness = 1.05f + level * 0.035f, scale = 1.025f, tilt = 1.5f)
        ListeningStage.RECOGNIZING -> AvatarPose(openness = 0.80f, gazeY = -0.08f, tilt = -1f)
        ListeningStage.NONE -> AvatarPose(openness = 1.04f)
    }
}

internal fun feedbackScript(kind: AvatarFeedbackKind): AvatarScript {
    val normal = AvatarPose()
    return when (kind) {
        AvatarFeedbackKind.TASK_SUCCEEDED, AvatarFeedbackKind.DEMO_SUCCEEDED -> avatarScript(
            0L to normal,
            150L to normal.copy(openness = 1.08f, scale = 1.025f),
            280L to normal.copy(openness = 0.96f, nod = 0.06f),
            400L to normal.copy(openness = 1.02f),
            560L to normal.copy(smile = 1f, nod = -0.025f, brightness = 1.08f),
            950L to normal.copy(smile = 1f, nod = -0.025f, brightness = 1.08f),
            1_200L to normal,
        )
        AvatarFeedbackKind.REPLY_READY -> avatarScript(
            0L to normal, 110L to normal.copy(openness = 1.04f),
            240L to normal.copy(nod = 0.035f, openness = 0.95f), 430L to normal,
        )
        AvatarFeedbackKind.NEEDS_ATTENTION -> avatarScript(
            0L to normal, 200L to normal.copy(tilt = 4f, asymmetry = 0.07f, openness = 1.03f),
            900L to normal.copy(tilt = 4f, asymmetry = 0.07f), 1_200L to normal,
        )
        AvatarFeedbackKind.FAILED -> avatarScript(
            0L to normal, 180L to normal.copy(openness = 0.76f, sadness = 1f, nod = 0.025f),
            270L to normal.copy(openness = 0.76f, sadness = 1f, gazeX = -0.06f),
            350L to normal.copy(openness = 0.76f, sadness = 1f, gazeX = 0.04f),
            650L to normal.copy(openness = 0.88f, sadness = 0.5f), 1_000L to normal,
        )
        AvatarFeedbackKind.UNCERTAIN -> avatarScript(
            0L to normal, 180L to normal.copy(tilt = -4f, openness = 0.85f, asymmetry = -0.06f),
            650L to normal.copy(tilt = -4f, openness = 0.90f, asymmetry = -0.06f),
            1_000L to normal,
        )
        AvatarFeedbackKind.CANCELLED -> avatarScript(
            0L to normal, 180L to normal.copy(nod = 0.025f, openness = 0.92f), 400L to normal,
        )
        AvatarFeedbackKind.SPEECH_NOT_UNDERSTOOD -> avatarScript(
            0L to normal, 180L to normal.copy(tilt = 5f, asymmetry = 0.09f),
            650L to normal.copy(tilt = 5f, asymmetry = 0.09f), 950L to normal,
        )
    }
}

internal fun speechBeatScript(started: Boolean): AvatarScript {
    val normal = AvatarPose()
    return if (started) avatarScript(
        0L to normal, 120L to normal.copy(gazeY = -0.04f, openness = 1.04f, nod = -0.015f),
        320L to normal,
    ) else avatarScript(
        0L to normal, 140L to normal.copy(nod = 0.025f, openness = 0.96f), 340L to normal,
    )
}

internal fun recognitionAcceptedScript(): AvatarScript = avatarScript(
    0L to AvatarPose(openness = 1.04f),
    130L to AvatarPose(nod = 0.035f, openness = 0.95f),
    280L to AvatarPose(),
    480L to AvatarPose(gazeX = 0.16f, gazeY = -0.24f, openness = 0.74f, tilt = -2f),
)

internal fun listeningNodScript(): AvatarScript {
    val listening = listeningPose(ListeningStage.SPEAKING, 0f)
    return avatarScript(0L to listening, 140L to listening.copy(nod = 0.025f), 360L to listening)
}

/** Silence is only a gesture cue; it never finalizes recognition or submits a task. */
internal class ListeningBackchannelGate {
    private var startedAt: Long? = null
    private var quietSince: Long? = null
    private var lastNodAt = 0L
    private var heardVoice = false
    private var nodCount = 0

    fun reset() {
        startedAt = null
        quietSince = null
        heardVoice = false
        nodCount = 0
    }

    fun observe(level: Float, nowMillis: Long): Boolean {
        val measured = level.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: return false
        if (startedAt == null) { startedAt = nowMillis; lastNodAt = nowMillis }
        if (measured >= 0.15f) { heardVoice = true; quietSince = null; return false }
        if (measured > 0.08f || !heardVoice) return false
        if (quietSince == null) quietSince = nowMillis
        if (nowMillis - requireNotNull(quietSince) < 250L || nowMillis - lastNodAt < 3_000L || nodCount >= 3) return false
        nodCount++
        lastNodAt = nowMillis
        heardVoice = false
        quietSince = null
        return true
    }
}

/** Consumed actions never replay after a touch, mode change, or an obsolete callback. */
internal class AvatarEventGate {
    private val latest = mutableMapOf<String, Long>()
    fun consume(channel: String, eventId: Long): Boolean {
        if (latest[channel]?.let { eventId <= it } == true) return false
        latest[channel] = eventId
        return true
    }
}

internal data class IdleSceneRun(val scene: IdleScene, val token: Long, val isPreview: Boolean = false)

internal class IdleSceneScheduler(private val random: Random = Random.Default) {
    var eligible: Boolean = false
        private set
    var generation: Long = 0L
        private set
    var nextAtMillis: Long? = null
        private set
    var active: IdleSceneRun? = null
        private set
    private var previousScene: IdleScene? = null
    private val playedAt = mutableMapOf<IdleScene, Long>()

    fun setEligible(value: Boolean, nowMillis: Long) {
        if (eligible == value) return
        eligible = value
        interrupt(nowMillis)
    }

    fun interrupt(nowMillis: Long) {
        generation++
        active = null
        nextAtMillis = if (eligible) nowMillis + random.nextLong(45_000L, 60_001L) else null
    }

    fun beginPreview(scene: IdleScene): IdleSceneRun {
        generation++
        nextAtMillis = null
        return IdleSceneRun(scene, generation, isPreview = true).also { active = it }
    }

    fun poll(nowMillis: Long): IdleSceneRun? {
        if (!eligible || active != null || nowMillis < (nextAtMillis ?: return null)) return null
        val candidates = IdleScene.entries.filter {
            it != previousScene && nowMillis - (playedAt[it] ?: Long.MIN_VALUE / 2) >= 240_000L
        }
        if (candidates.isEmpty()) {
            nextAtMillis = nowMillis + 30_000L
            return null
        }
        val scene = candidates[random.nextInt(candidates.size)]
        generation++
        previousScene = scene
        playedAt[scene] = nowMillis
        return IdleSceneRun(scene, generation).also { active = it }
    }

    fun finish(token: Long, nowMillis: Long): Boolean {
        if (active?.token != token || token != generation) return false
        active = null
        nextAtMillis = if (eligible) nowMillis + random.nextLong(120_000L, 240_001L) else null
        return true
    }
}
