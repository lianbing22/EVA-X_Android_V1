package com.evax.mobile.domain

import kotlinx.coroutines.flow.Flow

interface AssistantEngine {
    fun respond(prompt: String): Flow<AssistantEvent>
}
