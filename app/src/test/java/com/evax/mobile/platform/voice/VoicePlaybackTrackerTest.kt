package com.evax.mobile.platform.voice

import com.evax.mobile.presentation.VoicePlaybackEvent
import org.junit.Assert.*
import org.junit.Test

class VoicePlaybackTrackerTest {
    @Test
    fun queuedUtteranceDoesNotClaimActualPlayback() {
        val tracker = VoicePlaybackTracker()
        val queued = tracker.enqueue("first", true)
        assertFalse(queued.isSpeaking)
        assertEquals(1, queued.queuedCount)
        assertEquals(VoicePlaybackEvent.NONE, queued.playbackEvent)
        val started = tracker.onStart("first")
        assertTrue(started.isSpeaking)
        assertEquals(VoicePlaybackEvent.STARTED, started.playbackEvent)
        assertEquals("first", started.utteranceId)
    }

    @Test
    fun doneDoesNotTreatNextQueuedSentenceAsAlreadySpeaking() {
        val tracker = VoicePlaybackTracker()
        tracker.enqueue("first", true)
        tracker.enqueue("second", false)
        tracker.onStart("first")
        val done = tracker.onDone("first")
        assertFalse(done.isSpeaking)
        assertEquals(1, done.queuedCount)
        assertEquals(VoicePlaybackEvent.DONE, done.playbackEvent)
        assertTrue(tracker.onStart("second").isSpeaking)
    }

    @Test
    fun errorIsDistinctFromDoneAndDuplicateCallbackDoesNotReplayEvent() {
        val tracker = VoicePlaybackTracker()
        tracker.enqueue("first", true)
        tracker.onStart("first")
        val failed = tracker.onError("first", -7)
        assertFalse(failed.isSpeaking)
        assertEquals(VoicePlaybackEvent.ERROR, failed.playbackEvent)
        assertEquals(-7, failed.errorCode)
        assertEquals(failed, tracker.onDone("first"))
        assertEquals(failed, tracker.onError("first", -7))
    }

    @Test
    fun flushAndStopIgnoreAllLateCallbacksFromInvalidatedUtterances() {
        val tracker = VoicePlaybackTracker()
        tracker.enqueue("old", true)
        tracker.onStart("old")
        val new = tracker.enqueue("new", true)
        assertFalse(new.isSpeaking)
        assertEquals(new, tracker.onStart("old"))
        assertEquals(new, tracker.onDone("old"))
        tracker.onStart("new")
        val stopped = tracker.stop()
        assertEquals(VoicePlaybackEvent.STOPPED, stopped.playbackEvent)
        assertEquals(0, stopped.queuedCount)
        assertEquals(stopped, tracker.onStart("new"))
        assertEquals(stopped, tracker.onDone("new"))
    }

    @Test
    fun initializationDefersChunksAndReadyDrainsThemInOrderWithoutClaimingPlayback() {
        val tracker = VoicePlaybackTracker()
        tracker.defer("first", "第一句。", true)
        tracker.defer("second", "第二句。", false)
        assertFalse(tracker.state.isReady)
        assertFalse(tracker.state.isSpeaking)
        assertEquals(2, tracker.state.queuedCount)
        tracker.setReady(true)
        val queued = tracker.takeDeferred()
        assertEquals(listOf("第一句。", "第二句。"), queued.map { it.text })
        assertEquals(listOf(true, false), queued.map { it.flush })
        assertEquals(2, tracker.state.queuedCount)
        assertFalse(tracker.state.isSpeaking)
        assertTrue(tracker.onStart(queued.first().utteranceId).isSpeaking)
        assertFalse(tracker.onDone(queued.first().utteranceId).isSpeaking)
        assertEquals(1, tracker.state.queuedCount)
    }

    @Test
    fun flushWhileInitializingOnlyRetainsTheNewRound() {
        val tracker = VoicePlaybackTracker()
        tracker.defer("old-first", "旧第一句", true)
        tracker.defer("old-second", "旧第二句", false)
        tracker.defer("new", "新回复", true)
        assertEquals(1, tracker.state.queuedCount)
        tracker.setReady(true)
        assertEquals(listOf(DeferredSpeech("new", "新回复", true)), tracker.takeDeferred())
        val current = tracker.state
        assertEquals(current, tracker.onStart("old-first"))
        assertEquals(current, tracker.onDone("old-second"))
    }

    @Test
    fun stopBeforeReadyDropsDeferredSpeechAndIgnoresLateStarts() {
        val tracker = VoicePlaybackTracker()
        tracker.defer("pending", "不要播放这句", true)
        val stopped = tracker.stop()
        assertEquals(VoicePlaybackEvent.STOPPED, stopped.playbackEvent)
        assertEquals(0, stopped.queuedCount)
        tracker.setReady(true)
        assertTrue(tracker.takeDeferred().isEmpty())
        assertFalse(tracker.onStart("pending").isSpeaking)
        assertEquals(0, tracker.state.queuedCount)
    }

    @Test
    fun initializationOrLanguageFailureDropsDeferredSpeechAndPublishesError() {
        for (code in listOf(VoicePlaybackTracker.ERROR_INITIALIZATION, VoicePlaybackTracker.ERROR_LANGUAGE_UNAVAILABLE)) {
            val tracker = VoicePlaybackTracker()
            tracker.defer("pending", "首条回复", true)
            val failed = tracker.initializationFailed(code)
            assertFalse(failed.isReady)
            assertFalse(failed.isSpeaking)
            assertEquals(0, failed.queuedCount)
            assertEquals(VoicePlaybackEvent.ERROR, failed.playbackEvent)
            assertEquals(code, failed.errorCode)
            assertTrue(tracker.takeDeferred().isEmpty())
            assertEquals(failed, tracker.onStart("pending"))
        }
    }

    @Test
    fun pendingQueueIsBoundedByBothChunksAndCharacters() {
        val tracker = VoicePlaybackTracker(maxPendingUtterances = 2, maxPendingCharacters = 6)
        tracker.defer("first", "abc", true)
        tracker.defer("second", "def", false)
        tracker.defer("overflow", "x", false)
        assertEquals(2, tracker.state.queuedCount)
        assertEquals(VoicePlaybackTracker.ERROR_PENDING_LIMIT, tracker.state.errorCode)
        assertEquals(listOf("first", "second"), tracker.takeDeferred().map { it.utteranceId })
        tracker.defer("too-long", "1234567", true)
        assertEquals(0, tracker.state.queuedCount)
        assertTrue(tracker.takeDeferred().isEmpty())
        assertEquals(VoicePlaybackEvent.ERROR, tracker.state.playbackEvent)
    }
}
