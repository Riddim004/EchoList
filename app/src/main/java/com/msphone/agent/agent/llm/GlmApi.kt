package com.msphone.agent.agent.llm

import retrofit2.http.Body
import retrofit2.http.POST

/** 智谱开放平台 Chat Completions API */
interface GlmApi {

    @POST("chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}
