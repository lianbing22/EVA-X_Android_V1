package com.evax.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evax.mobile.presentation.AssistantPhase
import com.evax.mobile.presentation.AvatarFeedback
import com.evax.mobile.presentation.AvatarFeedbackKind
import com.evax.mobile.presentation.ListeningStage
import com.evax.mobile.presentation.VoicePlaybackEvent
import com.evax.mobile.presentation.VoicePlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Existing saved style values stay valid; each palette uses the same quiet Cozmo silhouette. */
enum class EvaEyeStyle(val label: String) {
    EVA_MINT("薄荷微光"),
    CYBER_COZMO("青绿灵眸"),
    GOLDEN_MECHA("暖琥珀"),
}

enum class EvaCompanionMode(val title: String, val subtitle: String) {
    COMPANION("陪伴", "我在呢，随时陪你聊"),
    DND("勿扰", "暂停自动播报，仍可主动提问"),
    REST("静息", "轻触唤醒"),
}

@Composable
fun AssistantAvatar(
    phase: AssistantPhase,
    modifier: Modifier = Modifier,
    diameter: Dp = 188.dp,
    isSpeaking: Boolean = false,
    faceOffset: Offset? = null,
    eyeStyle: EvaEyeStyle = EvaEyeStyle.EVA_MINT,
    companionMode: EvaCompanionMode = EvaCompanionMode.COMPANION,
    isLandscape: Boolean = false,
    micLevel: Float = 0f,
    reduceMotion: Boolean = false,
    onBackgroundTap: (() -> Unit)? = null,
    listeningStage: ListeningStage = ListeningStage.NONE,
    feedback: AvatarFeedback? = null,
    idleScenesAllowed: Boolean = false,
    previewScene: IdleScene? = null,
    onPreviewFinished: () -> Unit = {},
    onInteraction: () -> Unit = {},
    voicePlayback: VoicePlaybackState = VoicePlaybackState(),
) {
    val scope = rememberCoroutineScope()
    val player = remember(scope) { AvatarBehaviorPlayer(scope) }
    val scheduler = remember { IdleSceneScheduler() }
    val eventGate = remember { AvatarEventGate() }
    val backchannelGate = remember { ListeningBackchannelGate() }
    val blink = remember { Animatable(1f) }
    val wink = remember { Animatable(1f) }
    val scan = remember { Animatable(0f) }
    var idleGaze by remember { mutableStateOf(Offset.Zero) }
    var idleTilt by remember { mutableStateOf(0f) }
    var touchGaze by remember { mutableStateOf<Offset?>(null) }
    var tapSerial by remember { mutableIntStateOf(0) }
    var winkSerial by remember { mutableIntStateOf(0) }
    var handledWinkSerial by remember { mutableIntStateOf(0) }
    var previousPhase by remember { mutableStateOf(phase) }
    val companionActive = companionMode == EvaCompanionMode.COMPANION
    val resting = companionMode == EvaCompanionMode.REST
    val idle = phase == AssistantPhase.IDLE || phase == AssistantPhase.COMPLETED
    val audioBusy = isSpeaking || voicePlayback.isSpeaking || voicePlayback.queuedCount > 0
    val sceneEligible = idleScenesAllowed && companionActive && phase == AssistantPhase.IDLE &&
        listeningStage == ListeningStage.NONE && !audioBusy && !reduceMotion && feedback?.kind != AvatarFeedbackKind.NEEDS_ATTENTION
    val previewEligible = companionActive && idle && listeningStage == ListeningStage.NONE && !audioBusy && !reduceMotion &&
        feedback?.kind != AvatarFeedbackKind.NEEDS_ATTENTION
    val previewFinishedGate = remember(previewScene) { AvatarEventGate() }
    val currentPreview by rememberUpdatedState(previewScene)
    val currentPreviewFinished by rememberUpdatedState(onPreviewFinished)
    val currentInteraction by rememberUpdatedState(onInteraction)
    val currentBackgroundTap by rememberUpdatedState(onBackgroundTap)

    DisposableEffect(player) { onDispose { player.cancel() } }

    // Real state changes and touches take control before a lower-priority script can publish.
    LaunchedEffect(phase, companionMode, reduceMotion, listeningStage, tapSerial) {
        val oldPhase = previousPhase
        previousPhase = phase
        player.cancel()
        scheduler.interrupt(System.nanoTime() / 1_000_000L)
        if (!resting && !reduceMotion && phase == AssistantPhase.THINKING && oldPhase == AssistantPhase.LISTENING) {
            player.play(recognitionAcceptedScript(), requestedPriority = 3)
        } else if (!resting && !reduceMotion && feedback == null && phase == AssistantPhase.COMPLETED && oldPhase != phase) {
            // Older callers can express a ready reply, but cannot assert execution success.
            player.play(feedbackScript(AvatarFeedbackKind.REPLY_READY), requestedPriority = 3)
        }
    }

    LaunchedEffect(feedback?.eventId, feedback?.kind) {
        val event = feedback ?: return@LaunchedEffect
        if (!eventGate.consume("feedback", event.eventId) || resting || reduceMotion) return@LaunchedEffect
        player.play(feedbackScript(event.kind), requestedPriority = 3)
    }

    // Playback callbacks provide sentence beats, never measured output audio or task success.
    LaunchedEffect(voicePlayback.eventSequence) {
        if (!eventGate.consume("tts", voicePlayback.eventSequence) || resting || reduceMotion || phase == AssistantPhase.LISTENING) return@LaunchedEffect
        when (voicePlayback.playbackEvent) {
            VoicePlaybackEvent.STARTED -> { player.cancelScene(); player.play(speechBeatScript(true), requestedPriority = 1) }
            VoicePlaybackEvent.DONE -> player.play(speechBeatScript(false), requestedPriority = 1)
            VoicePlaybackEvent.ERROR, VoicePlaybackEvent.STOPPED -> player.cancelScene()
            VoicePlaybackEvent.NONE -> Unit
        }
    }

    // The same cancellable loop services automatic scenes and a single explicit preview.
    LaunchedEffect(sceneEligible, previewScene, previewEligible, tapSerial) {
        val now = System.nanoTime() / 1_000_000L
        scheduler.setEligible(sceneEligible, now)
        player.cancelScene()
        val requestedPreview = previewScene
        if (requestedPreview != null) {
            var previewJob: kotlinx.coroutines.Job? = null
            try {
                if (previewEligible) {
                    val run = scheduler.beginPreview(requestedPreview)
                    previewJob = player.play(idleSceneScript(run.scene), run.scene)
                    previewJob?.join()
                    scheduler.finish(run.token, System.nanoTime() / 1_000_000L)
                }
            } finally {
                player.cancelRun(previewJob)
                if (currentPreview == requestedPreview && previewFinishedGate.consume("preview", 0L)) currentPreviewFinished()
            }
            return@LaunchedEffect
        }
        if (!sceneEligible) return@LaunchedEffect
        while (isActive) {
            val deadline = scheduler.nextAtMillis ?: break
            delay((deadline - System.nanoTime() / 1_000_000L).coerceAtLeast(1L))
            val run = scheduler.poll(System.nanoTime() / 1_000_000L) ?: continue
            val sceneJob = player.play(idleSceneScript(run.scene), run.scene)
            try {
                sceneJob?.join()
            } finally {
                scheduler.finish(run.token, System.nanoTime() / 1_000_000L)
                player.cancelRun(sceneJob)
            }
        }
    }

    val autonomousGaze = companionActive && idle && !reduceMotion && !audioBusy && player.frame == null && touchGaze == null
    LaunchedEffect(autonomousGaze) {
        idleGaze = Offset.Zero
        idleTilt = 0f
        if (!autonomousGaze) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(1_800L, 5_001L))
            idleGaze = if (Random.nextFloat() < 0.35f) Offset.Zero else
                Offset(Random.nextFloat() * 0.84f - 0.42f, Random.nextFloat() * 0.30f - 0.15f)
            idleTilt = if (Random.nextFloat() < 0.18f) Random.nextFloat() * 6f - 3f else 0f
        }
    }

    // Phase and scene changes deliberately do not reset the natural blink clock.
    LaunchedEffect(companionActive, reduceMotion) {
        blink.snapTo(1f)
        if (!companionActive || reduceMotion) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(2_500L, 6_501L))
            blink.animateTo(0.06f, tween(65, easing = FastOutSlowInEasing))
            blink.animateTo(1f, tween(125, easing = FastOutSlowInEasing))
            if (Random.nextFloat() < 0.10f) {
                delay(120)
                blink.animateTo(0.06f, tween(65))
                blink.animateTo(1f, tween(120))
            }
        }
    }

    LaunchedEffect(touchGaze, companionMode) {
        if (!companionActive) touchGaze = null
        if (touchGaze != null) { delay(2_000); touchGaze = null }
    }
    LaunchedEffect(winkSerial, companionMode, reduceMotion) {
        val newWink = winkSerial != handledWinkSerial
        handledWinkSerial = winkSerial
        wink.snapTo(1f)
        if (!newWink || !companionActive || reduceMotion) return@LaunchedEffect
        wink.animateTo(0.06f, tween(85)); delay(180); wink.animateTo(1f, tween(135))
    }
    LaunchedEffect(phase, companionMode, reduceMotion) {
        scan.snapTo(0f)
        if (phase != AssistantPhase.EXECUTING || resting || reduceMotion) return@LaunchedEffect
        while (isActive) {
            scan.snapTo(0f)
            scan.animateTo(1f, tween(2_300, easing = LinearEasing))
            delay(800)
        }
    }

    val measuredLevel = micLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val currentMeasuredLevel by rememberUpdatedState(measuredLevel)
    LaunchedEffect(phase, listeningStage, companionMode, reduceMotion) {
        if (phase != AssistantPhase.LISTENING || listeningStage != ListeningStage.SPEAKING || resting || reduceMotion) {
            backchannelGate.reset()
            return@LaunchedEffect
        }
        while (isActive) {
            if (backchannelGate.observe(currentMeasuredLevel, System.nanoTime() / 1_000_000L)) {
                player.play(listeningNodScript(), requestedPriority = 2)
            }
            delay(100)
        }
    }
    val statePose = when {
        resting -> AvatarPose(openness = 0.06f, brightness = 0.4f)
        phase == AssistantPhase.LISTENING -> listeningPose(listeningStage, measuredLevel)
        phase == AssistantPhase.THINKING -> AvatarPose(gazeX = 0.16f, gazeY = -0.24f, openness = 0.74f, tilt = -2f)
        phase == AssistantPhase.EXECUTING -> AvatarPose(openness = 0.82f)
        phase == AssistantPhase.ERROR && feedback?.kind == AvatarFeedbackKind.UNCERTAIN -> AvatarPose(openness = 0.89f, tilt = -4f, asymmetry = -0.06f)
        phase == AssistantPhase.ERROR && reduceMotion -> AvatarPose(openness = 0.86f, sadness = 0.55f)
        phase == AssistantPhase.ERROR -> AvatarPose(openness = if (feedback == null) 0.86f else 0.95f,
            sadness = if (feedback == null) 0.65f else 0f)
        feedback?.kind == AvatarFeedbackKind.NEEDS_ATTENTION -> AvatarPose(tilt = 3f, asymmetry = 0.06f)
        companionMode == EvaCompanionMode.DND -> AvatarPose(openness = 0.58f, brightness = 0.65f)
        else -> AvatarPose(gazeX = idleGaze.x, gazeY = idleGaze.y, tilt = idleTilt)
    }
    val cameraGaze = faceOffset?.takeIf { it.x.isFinite() && it.y.isFinite() }
    val baseAttention = when {
        resting -> Offset.Zero
        touchGaze != null -> touchGaze!!
        cameraGaze != null -> Offset(
            statePose.gazeX + cameraGaze.x.coerceIn(-0.65f, 0.65f) * if (idle) 0.85f else 0.35f,
            statePose.gazeY + cameraGaze.y.coerceIn(-0.45f, 0.45f) * if (idle) 0.85f else 0.35f,
        )
        else -> Offset(statePose.gazeX, statePose.gazeY)
    }
    val gazeX by animateFloatAsState(if (reduceMotion) 0f else baseAttention.x.coerceIn(-0.65f, 0.65f), tween(if (reduceMotion) 0 else 130), label = "eye-gaze-x")
    val gazeY by animateFloatAsState(if (reduceMotion) 0f else baseAttention.y.coerceIn(-0.45f, 0.45f), tween(if (reduceMotion) 0 else 130), label = "eye-gaze-y")
    val tilt by animateFloatAsState(if (reduceMotion) 0f else statePose.tilt, tween(if (reduceMotion) 0 else 320), label = "eye-head-tilt")
    val openness by animateFloatAsState(statePose.openness, tween(if (reduceMotion) 0 else 160), label = "eye-openness")
    val scale by animateFloatAsState(if (reduceMotion) 1f else statePose.scale, tween(if (reduceMotion) 0 else 180), label = "eye-scale")
    val asymmetry by animateFloatAsState(statePose.asymmetry, tween(if (reduceMotion) 0 else 160), label = "eye-asymmetry")
    val sadness by animateFloatAsState(statePose.sadness, tween(if (reduceMotion) 0 else 180), label = "eye-sadness")
    val exposure by animateFloatAsState(if (phase == AssistantPhase.LISTENING) measuredLevel else 0f, tween(if (reduceMotion) 0 else 90), label = "measured-eye-audio")
    val staticSmile = if (reduceMotion && phase == AssistantPhase.COMPLETED && feedback?.kind in
        listOf(AvatarFeedbackKind.TASK_SUCCEEDED, AvatarFeedbackKind.DEMO_SUCCEEDED)) 1f else 0f
    val basePose = statePose.copy(gazeX = gazeX, gazeY = gazeY, tilt = tilt, openness = openness,
        scale = scale, asymmetry = asymmetry, sadness = sadness, smile = staticSmile)
    val behavior = player.frame
    val pose = if (reduceMotion || resting) basePose else behavior?.pose ?: basePose
    val accent = when (eyeStyle) {
        EvaEyeStyle.GOLDEN_MECHA -> Color(0xFFE5BC68)
        EvaEyeStyle.EVA_MINT, EvaEyeStyle.CYBER_COZMO -> Color(0xFF45E5CC)
    }
    val semanticLabel = when {
        behavior?.scene != null -> "待机小剧场，${behavior.scene.label}，虚构场景"
        resting -> companionMode.title
        phase == AssistantPhase.LISTENING -> when (listeningStage) {
            ListeningStage.PREPARING -> "正在准备麦克风"
            ListeningStage.READY -> "请说话"
            ListeningStage.RECOGNIZING -> "正在识别"
            else -> "正在聆听"
        }
        phase == AssistantPhase.COMPLETED && feedback?.kind == AvatarFeedbackKind.TASK_SUCCEEDED -> "任务成功"
        phase == AssistantPhase.COMPLETED && feedback?.kind == AvatarFeedbackKind.DEMO_SUCCEEDED -> "演示成功"
        else -> phaseLabel(phase)
    }
    val currentEyeGaze by rememberUpdatedState(Offset(pose.gazeX, pose.gazeY))

    Box(modifier.fillMaxWidth().defaultMinSize(minHeight = diameter)) {
        Canvas(Modifier.matchParentSize()
            .pointerInput(companionMode, isLandscape) {
                fun touchesEye(position: Offset): Boolean {
                    val width = min(size.width * if (isLandscape) 0.255f else 0.27f, size.height * 0.53f)
                    val height = min(size.width * 0.31f, size.height * 0.45f)
                    val center = Offset(size.width / 2f, size.height * 0.43f) + Offset(
                        currentEyeGaze.x * width * 0.28f, currentEyeGaze.y * height * 0.22f)
                    return abs(abs(position.x - center.x) - width * 0.72f) <= width * 0.63f &&
                        abs(position.y - center.y) <= height * 0.62f
                }
                fun interrupt() {
                    player.cancel()
                    scheduler.interrupt(System.nanoTime() / 1_000_000L)
                    tapSerial++
                    currentInteraction()
                }
                detectTapGestures(
                    onPress = { interrupt(); tryAwaitRelease() },
                    onTap = { position ->
                        if (companionMode != EvaCompanionMode.COMPANION || !touchesEye(position)) currentBackgroundTap?.invoke()
                        else touchGaze = Offset(((position.x / size.width) - 0.5f).coerceIn(-0.5f, 0.5f) * 1.3f,
                            ((position.y / size.height) - 0.43f).coerceIn(-0.5f, 0.5f) * 0.8f)
                    },
                    onDoubleTap = { position ->
                        if (companionMode == EvaCompanionMode.COMPANION && touchesEye(position)) {
                            touchGaze = null; winkSerial++
                        } else currentBackgroundTap?.invoke()
                    },
                )
            }.semantics { contentDescription = "EVA 双眼，$semanticLabel" }
        ) {
            val eyeWidth = min(size.width * if (isLandscape) 0.255f else 0.27f, size.height * 0.53f)
            val eyeHeight = min(size.width * 0.31f, size.height * 0.45f)
            val spacing = eyeWidth * 0.72f
            val sceneCenter = Offset(size.width / 2f, size.height * 0.43f)
            val eyeCenter = sceneCenter + Offset(pose.gazeX * eyeWidth * 0.28f, (pose.gazeY * 0.22f + pose.nod) * eyeHeight)
            val intensity = ((if (resting) 0.36f else 0.88f) * pose.brightness + exposure * 0.07f).coerceIn(0f, 1f)
            withTransform({ rotate(pose.tilt, sceneCenter); scale(pose.scale, pose.scale, sceneCenter) }) {
                for (index in 0..1) {
                    val left = index == 0
                    val perspective = (1f + pose.gazeX * if (left) -0.09f else 0.09f).coerceIn(0.94f, 1.06f)
                    val center = eyeCenter + Offset(if (left) -spacing else spacing, if (left) -pose.asymmetry * eyeHeight * 0.10f else pose.asymmetry * eyeHeight * 0.10f)
                    val eyeBlink = if (companionActive && !reduceMotion) blink.value * if (left) 1f else wink.value else 1f
                    val width = eyeWidth * perspective * pose.width
                    val height = eyeHeight * perspective * (pose.openness + if (left) pose.asymmetry else -pose.asymmetry).coerceAtLeast(0.05f) * eyeBlink
                    withTransform({ rotate(pose.sadness * if (left) -9f else 9f, center) }) {
                        if (resting) drawClosedEye(center, width * 0.9f, eyeHeight * 0.15f, accent, intensity, happy = false)
                        else drawMinimalEye(center, width, height, accent, intensity, scan.value,
                            phase == AssistantPhase.EXECUTING && behavior == null, pose.smile * eyeBlink)
                    }
                }
            }
            if (behavior?.scene != null && !resting && !reduceMotion) {
                drawIdleSceneProp(behavior.scene, behavior.progress, pose.propAlpha, sceneCenter, eyeWidth, eyeHeight, accent)
            }
        }
        if (behavior?.scene != null && !resting && !reduceMotion) {
            Text(
                text = "休闲小剧场 · ${behavior.scene.label}",
                color = accent.copy(alpha = 0.50f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    .testTag("avatar-idle-scene")
                    .semantics { contentDescription = "休闲小剧场，${behavior.scene.label}" },
            )
        }
    }
}

private fun DrawScope.drawMinimalEye(
    center: Offset,
    width: Float,
    height: Float,
    accent: Color,
    intensity: Float,
    scan: Float,
    executing: Boolean,
    smile: Float = 0f,
) {
    if (intensity <= 0f) return
    val curve = smile.coerceIn(0f, 1f)
    val safeHeight = (height * (1f - curve) + width * 0.085f * curve).coerceAtLeast(width * 0.045f)
    val rect = Rect(center - Offset(width / 2f, safeHeight / 2f), Size(width, safeHeight))
    val corner = min(width * 0.29f, safeHeight / 2f)
    val lift = width * 0.29f * curve
    val edgeDrop = lift * 0.30f
    val outline = Path().apply {
        if (curve < 0.001f) addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
        else {
            moveTo(rect.left + corner, rect.top + edgeDrop)
            quadraticTo(center.x, rect.top - lift, rect.right - corner, rect.top + edgeDrop)
            quadraticTo(rect.right, rect.top + edgeDrop, rect.right, rect.top + corner + edgeDrop)
            lineTo(rect.right, rect.bottom - corner + edgeDrop)
            quadraticTo(rect.right, rect.bottom + edgeDrop, rect.right - corner, rect.bottom + edgeDrop)
            quadraticTo(center.x, rect.bottom - lift, rect.left + corner, rect.bottom + edgeDrop)
            quadraticTo(rect.left, rect.bottom + edgeDrop, rect.left, rect.bottom - corner + edgeDrop)
            lineTo(rect.left, rect.top + corner + edgeDrop)
            quadraticTo(rect.left, rect.top + edgeDrop, rect.left + corner, rect.top + edgeDrop)
            close()
        }
    }
    // The glow stays close to each silhouette so the surrounding OLED scene stays black.
    for ((expansion, alpha) in listOf(0.08f to 0.028f, 0.04f to 0.055f, 0.015f to 0.10f)) {
        val padding = width * expansion
        if (curve < 0.001f) drawRoundRect(accent.copy(alpha = alpha * intensity),
            rect.topLeft - Offset(padding, padding), Size(width + padding * 2f, safeHeight + padding * 2f),
            CornerRadius(corner + padding, corner + padding))
        else drawPath(outline, accent.copy(alpha = alpha * intensity), style = Stroke(padding * 2f))
    }
    drawPath(
        path = outline,
        brush = Brush.verticalGradient(
            colors = listOf(accent.copy(alpha = intensity), accent.copy(alpha = intensity * 0.83f)),
            startY = rect.top,
            endY = rect.bottom,
        ),
    )
    if (executing) {
        clipPath(outline) {
            val scanCenter = Offset(rect.left + width * scan, rect.center.y)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f * intensity), Color.Transparent),
                    center = scanCenter,
                    radius = width * 0.40f,
                ),
                radius = width * 0.40f,
                center = scanCenter,
            )
        }
    }
}

private fun DrawScope.drawClosedEye(
    center: Offset,
    width: Float,
    archHeight: Float,
    accent: Color,
    intensity: Float,
    happy: Boolean,
) {
    if (intensity <= 0f) return
    val direction = if (happy) -1f else 1f
    val path = Path().apply {
        moveTo(center.x - width / 2f, center.y + if (happy) archHeight * 0.24f else -archHeight * 0.24f)
        quadraticTo(center.x, center.y + direction * archHeight * 1.4f, center.x + width / 2f, center.y + if (happy) archHeight * 0.24f else -archHeight * 0.24f)
    }
    drawPath(path, accent.copy(alpha = intensity * 0.10f), style = Stroke(width * 0.16f, cap = StrokeCap.Round))
    drawPath(path, accent.copy(alpha = intensity), style = Stroke(width * 0.085f, cap = StrokeCap.Round))
}

/** The waveform receives measured audio levels; task phases never create fake microphone activity. */
@Composable
fun SymmetricAudioWaveform(
    phase: AssistantPhase,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    micLevel: Float = 0f,
    reduceMotion: Boolean = false,
) {
    val audioActive = phase == AssistantPhase.LISTENING || isSpeaking
    val measuredLevel = micLevel.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val level by animateFloatAsState(
        targetValue = if (audioActive) measuredLevel else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 90),
        label = "measured-audio-level",
    )
    Canvas(modifier = modifier.width(84.dp).height(24.dp)) {
        val barCount = 11
        val spacing = size.width / (barCount + 1)
        val centerIndex = (barCount - 1) / 2f
        val accent = phaseAccentColor(phase)
        for (index in 0 until barCount) {
            val envelope = 1f - abs(index - centerIndex) / (centerIndex + 1f)
            val shape = 0.62f + 0.38f * abs(sin(index * 1.8f))
            val barHeight = maxOf(2.dp.toPx(), size.height * level * envelope * shape)
            val x = spacing * (index + 1)
            drawLine(
                color = accent.copy(alpha = if (audioActive) 0.42f + level * 0.45f else 0.20f),
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

internal fun phaseAccentColor(phase: AssistantPhase): Color = when (phase) {
    AssistantPhase.ERROR -> Color(0xFFF0A893)
    else -> Color(0xFF45E5CC)
}

internal fun phaseLabel(phase: AssistantPhase): String = when (phase) {
    AssistantPhase.IDLE -> "准备就绪"
    AssistantPhase.LISTENING -> "正在聆听"
    AssistantPhase.THINKING -> "正在思考"
    AssistantPhase.EXECUTING -> "正在执行"
    AssistantPhase.COMPLETED -> "回复就绪"
    AssistantPhase.ERROR -> "遇到一点问题"
}
