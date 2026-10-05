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
}
