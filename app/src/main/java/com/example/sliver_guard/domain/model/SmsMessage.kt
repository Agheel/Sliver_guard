package com.example.sliver_guard.domain.model

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)
