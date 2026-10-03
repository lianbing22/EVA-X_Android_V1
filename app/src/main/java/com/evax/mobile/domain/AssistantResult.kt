package com.evax.mobile.domain

data class AssistantResult(
    val text: String,
    val sampleLabel: String,
    val followUps: List<String> = emptyList(),
)
