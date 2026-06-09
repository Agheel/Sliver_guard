package com.angae.phishingdefender.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSms
import com.angae.phishingdefender.domain.detector.PhishingProcessor
import com.angae.phishingdefender.domain.model.SmsMessage
import com.angae.phishingdefender.ui.alert.AlertActivity

/**
 * [SRP] 시스템 브로드캐스트를 수신하여 도메인 모델로 변환하고 판별 프로세서에 위임하는 역할만 수행함.
 */
class SMSReceiver : BroadcastReceiver() {

    // [DIP] 구체적인 탐지기가 아닌 인터페이스의 집합체인 PhishingProcessor 추상화에 의존함.
    private val processor = PhishingProcessor.createDefault()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            // [SRP] 여러 파트로 나뉜 메시지를 하나로 합침
            val sender = messages[0].displayOriginatingAddress ?: "Unknown"
            val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

            // [DIP] 안드로이드 프레임워크 객체를 순수 도메인 모델로 변환하여 시스템 의존성 전파를 차단함.
            val domainMessage = SmsMessage(
                sender = sender,
                body = fullBody
            )

            // [DIP] 판별 로직은 직접 수행하지 않고 전문 프로세서에게 위임함.
            val result = processor.process(domainMessage)

            if (result.isPhishing) {
                startAlertActivity(context, domainMessage, result)
            }
        }
    }

    private fun startAlertActivity(
        context: Context,
        message: SmsMessage,
        result: com.angae.phishingdefender.domain.detector.DetectionResult
    ) {
        val intent = Intent(context, AlertActivity::class.java).apply {
            // [ISP] 필요한 정보만 골라 Intent Extra로 전달함.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("sender", message.sender)
            putExtra("body", message.body)
            putExtra("reason", result.reason)
            putStringArrayListExtra("matched", ArrayList(result.matched))
        }
        context.startActivity(intent)
    }
}
