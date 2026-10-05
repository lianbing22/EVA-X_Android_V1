package com.evax.mobile.ui

import com.evax.mobile.presentation.AvatarFeedbackKind
import com.evax.mobile.presentation.ListeningStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AvatarBehaviorTest {
    @Test fun everySceneHasACompleteBoundedTimelineAndReturnsToNeutral() {
        assertEquals(8, IdleScene.entries.size)
        for (scene in IdleScene.entries) {
            val script = idleSceneScript(scene)
            assertEquals(scene.durationMillis, script.durationMillis)
            assertTrue(script.durationMillis in 4_000L..8_000L)
            assertEquals(AvatarPose(), script.sample(0))
            assertEquals(AvatarPose(), script.sample(script.durationMillis))
            for (time in 0L..script.durationMillis step 17L) {
                val pose = script.sample(time)
                assertTrue(pose.gazeX.isFinite() && pose.gazeY.isFinite())
                assertTrue(pose.openness in 0.05f..1.2f)
                assertTrue(pose.scale in 1f..1.05f)
                assertTrue(pose.propAlpha in 0f..1f)
                assertTrue(pose.smile in 0f..1f)
                assertTrue(kotlin.math.abs(pose.nod) <= 0.06f)
            }
        }
    }

    @Test fun surfingGlancesQuicklyAndThenActuallyHoldsAttention() {
        val script = idleSceneScript(IdleScene.SURFING)
        assertEquals(-0.22f, script.sample(720).gazeX, 0.001f)
        assertEquals(script.sample(750).gazeX, script.sample(1_200).gazeX, 0.001f)
        assertEquals(0.24f, script.sample(1_390).gazeX, 0.001f)
        assertTrue(script.sample(3_600).smile > 0.9f)
    }

    @Test fun successHasTheFullOnePointTwoSecondSequence() {
        val script = feedbackScript(AvatarFeedbackKind.TASK_SUCCEEDED)
        assertEquals(1_200L, script.durationMillis)
        assertTrue(script.sample(150).openness > 1f)
        assertTrue(script.sample(280).nod > 0f)
        assertEquals(1f, script.sample(700).smile, 0.001f)
        assertEquals(AvatarPose(), script.sample(1_200))
        assertEquals(script.sample(700), feedbackScript(AvatarFeedbackKind.DEMO_SUCCEEDED).sample(700))
    }

    @Test fun replyUncertaintyFailureAndCancellationNeverCelebrate() {
        val kinds = listOf(AvatarFeedbackKind.REPLY_READY, AvatarFeedbackKind.NEEDS_ATTENTION,
            AvatarFeedbackKind.FAILED, AvatarFeedbackKind.UNCERTAIN, AvatarFeedbackKind.CANCELLED,
            AvatarFeedbackKind.SPEECH_NOT_UNDERSTOOD)
        for (kind in kinds) {
            val script = feedbackScript(kind)
            assertTrue(script.frames.all { it.pose.smile == 0f })
            assertEquals(AvatarPose(), script.sample(script.durationMillis))
        }
        assertTrue(feedbackScript(AvatarFeedbackKind.FAILED).sample(250).sadness > 0.9f)
        assertEquals(0f, feedbackScript(AvatarFeedbackKind.UNCERTAIN).sample(250).sadness, 0.001f)
    }

    @Test fun speechBeatsCannotProduceSuccessExpressions() {
        for (started in listOf(true, false)) {
            assertTrue(speechBeatScript(started).frames.all { it.pose.smile == 0f })
            assertTrue(speechBeatScript(started).durationMillis < 500L)
        }
    }

    @Test fun listeningStagesHaveDistinctPosesAndUseOnlyFiniteMeasuredLevels() {
        val preparing = listeningPose(ListeningStage.PREPARING, 0f)
        val ready = listeningPose(ListeningStage.READY, 0f)
        val recognizing = listeningPose(ListeningStage.RECOGNIZING, 1f)
        assertTrue(ready.openness > preparing.openness)
        assertTrue(ready.tilt in 3f..5f)
        assertTrue(recognizing.openness < preparing.openness)
        assertEquals(listeningPose(ListeningStage.SPEAKING, 0f), listeningPose(ListeningStage.SPEAKING, Float.NaN))
        assertTrue(listeningPose(ListeningStage.SPEAKING, 1f).openness > listeningPose(ListeningStage.SPEAKING, 0f).openness)
        assertEquals(recognizing, listeningPose(ListeningStage.RECOGNIZING, 0f))
    }

    @Test fun eventGateRejectsDuplicatesAndLateEventsIndependentlyPerChannel() {
        val gate = AvatarEventGate()
        assertTrue(gate.consume("feedback", 1))
        assertFalse(gate.consume("feedback", 1))
        assertTrue(gate.consume("tts", 1))
        assertTrue(gate.consume("feedback", 99))
        assertFalse(gate.consume("feedback", 2))
        assertTrue(gate.consume("tts", 2))
    }

    @Test fun listeningNodsRequireRealVoiceThenQuietAndHaveThreeSecondSpacing() {
        val gate = ListeningBackchannelGate()
        assertFalse(gate.observe(0f, 0))
        assertFalse(gate.observe(0f, 4_000))
        assertFalse(gate.observe(0.6f, 4_010))
        assertFalse(gate.observe(0f, 4_200))
        assertTrue(gate.observe(0f, 4_500))
        assertFalse(gate.observe(0f, 9_000))
        assertFalse(gate.observe(0.7f, 9_100))
        assertFalse(gate.observe(0f, 9_200))
        assertTrue(gate.observe(0f, 9_500))
        assertFalse(gate.observe(0.7f, 9_600))
        assertFalse(gate.observe(0f, 9_700))
        assertFalse(gate.observe(0f, 10_000))
        assertTrue(gate.observe(0f, 12_500))
        assertFalse(gate.observe(0.7f, 13_000))
        assertFalse(gate.observe(0f, 13_200))
        assertFalse(gate.observe(0f, 16_500))
        gate.reset()
        assertFalse(gate.observe(0.7f, 20_000))
        assertFalse(gate.observe(0f, 23_100))
        assertTrue(gate.observe(0f, 23_500))
    }

    @Test fun firstAutomaticSceneWaitsFortyFiveToSixtySeconds() {
        val scheduler = IdleSceneScheduler(Random(7))
        assertNull(scheduler.poll(1_000_000))
        scheduler.setEligible(true, 0)
        val deadline = requireNotNull(scheduler.nextAtMillis)
        assertTrue(deadline in 45_000L..60_000L)
        assertNull(scheduler.poll(deadline - 1))
        assertTrue(scheduler.poll(deadline) != null)
    }

    @Test fun subsequentScenesWaitTwoToFourMinutesAndAvoidImmediateRepeats() {
        val scheduler = IdleSceneScheduler(Random(11))
        scheduler.setEligible(true, 0)
        var now = requireNotNull(scheduler.nextAtMillis)
        var previous: IdleScene? = null
        repeat(24) {
            val run = requireNotNull(scheduler.poll(now))
            assertNotEquals(previous, run.scene)
            val finishedAt = now + run.scene.durationMillis
            assertTrue(scheduler.finish(run.token, finishedAt))
            val deadline = requireNotNull(scheduler.nextAtMillis)
            assertTrue(deadline - finishedAt in 120_000L..240_000L)
            previous = run.scene
            now = deadline
        }
    }

    @Test fun cancellationInvalidatesOldSceneAndRestartsQuietInterval() {
        val scheduler = IdleSceneScheduler(Random(12))
        scheduler.setEligible(true, 0)
        val old = requireNotNull(scheduler.poll(requireNotNull(scheduler.nextAtMillis)))
        scheduler.interrupt(70_000)
        assertNull(scheduler.active)
        assertFalse(scheduler.finish(old.token, 80_000))
        assertTrue(requireNotNull(scheduler.nextAtMillis) in 115_000L..130_000L)
        scheduler.setEligible(false, 90_000)
        assertNull(scheduler.nextAtMillis)
        assertNull(scheduler.poll(Long.MAX_VALUE / 2))
    }

    @Test fun previewIsIndependentOfAutomaticEligibilityAndOldFinishCannotClearNewPreview() {
        val scheduler = IdleSceneScheduler(Random(15))
        val old = scheduler.beginPreview(IdleScene.SURFING)
        assertFalse(scheduler.eligible)
        val next = scheduler.beginPreview(IdleScene.READING)
        assertFalse(scheduler.finish(old.token, 6_000))
        assertEquals(next, scheduler.active)
        assertTrue(scheduler.finish(next.token, 12_000))
        assertNull(scheduler.active)
        assertNull(scheduler.nextAtMillis)
    }

    @Test fun sceneCooldownIsAtLeastFourMinutes() {
        val scheduler = IdleSceneScheduler(Random(23))
        val played = mutableMapOf<IdleScene, Long>()
        scheduler.setEligible(true, 0)
        var now = requireNotNull(scheduler.nextAtMillis)
        repeat(32) {
            val run = requireNotNull(scheduler.poll(now))
            played[run.scene]?.let { assertTrue(now - it >= 240_000L) }
            played[run.scene] = now
            scheduler.finish(run.token, now + run.scene.durationMillis)
            now = requireNotNull(scheduler.nextAtMillis)
        }
    }
}
