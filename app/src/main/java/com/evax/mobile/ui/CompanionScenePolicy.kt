package com.evax.mobile.ui

/** Explicit input and unfinished work take priority over local idle stories. */
internal data class CompanionSceneConditions(
    val foreground: Boolean = true,
    val companionMode: Boolean = true,
    val enabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val powerSaving: Boolean = false,
    val processing: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val queuedSpeech: Boolean = false,
    val needsAttention: Boolean = false,
    val hasNotice: Boolean = false,
    val panelOpen: Boolean = false,
    val editing: Boolean = false,
    val checkingConnection: Boolean = false,
) {
    val allowsScenes: Boolean
        get() = foreground && companionMode && enabled && !reduceMotion && !powerSaving &&
            !processing && !listening && !speaking && !queuedSpeech && !needsAttention &&
            !hasNotice && !panelOpen && !editing && !checkingConnection
}
