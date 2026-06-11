package com.example.sliver_guard.domain.notifier

import com.example.sliver_guard.domain.model.SmsMessage

interface GuardianNotifier {
    suspend fun notifyGuardian(message: SmsMessage, reason: String, elderId: String): Boolean
}
