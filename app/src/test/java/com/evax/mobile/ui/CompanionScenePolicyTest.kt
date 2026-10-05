package com.evax.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionScenePolicyTest {
    @Test fun quietForegroundAllowsStories() {
        assertTrue(CompanionSceneConditions().allowsScenes)
    }

    @Test fun everyInteractionAndUnfinishedStateBlocksStories() {
        val base = CompanionSceneConditions()
        listOf(
            base.copy(foreground = false), base.copy(companionMode = false),
            base.copy(enabled = false), base.copy(reduceMotion = true),
            base.copy(powerSaving = true), base.copy(processing = true),
            base.copy(listening = true), base.copy(speaking = true),
            base.copy(queuedSpeech = true), base.copy(needsAttention = true),
            base.copy(hasNotice = true), base.copy(panelOpen = true),
            base.copy(editing = true), base.copy(checkingConnection = true),
        ).forEach { assertFalse(it.toString(), it.allowsScenes) }
    }
}
