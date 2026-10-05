package com.evax.mobile.domain

enum class AssistantOutcome {
    REPLY_RECEIVED, TASK_SUCCEEDED, NEEDS_ATTENTION, FAILED, UNCERTAIN, CANCELLED,
}

data class ExecutionEvidence(
    val source: String,
    val status: String,
    val verified: Boolean = false,
    val terminal: Boolean = false,
)

data class AssistantResult(
    val text: String,
    val sampleLabel: String,
    val followUps: List<String> = emptyList(),
    val isSample: Boolean = false,
    val source: AssistantSource = AssistantSource.UNCONFIRMED,
    val outcome: AssistantOutcome = AssistantOutcome.REPLY_RECEIVED,
    val taskId: String? = null,
    val requestId: String? = null,
    val evidence: ExecutionEvidence? = null,
)
