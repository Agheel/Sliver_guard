package com.angae.phishingdefender.domain.model

/**
 * [SRP] 시스템(Android SMS) 의존성을 제거한 순수 도메인 데이터 모델.
 */
data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)
