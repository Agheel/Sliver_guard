package com.angae.phishingdefender.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSms
import com.angae.phishingdefender.domain.detector.PhishingProcessor
import com.angae.phishingdefender.domain.model.SmsMessage
import com.angae.phishingdefender.ui.alert.AlertActivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [SRP] 시스템 브로드캐스트를 수신하여 도메인 모델로 변환하고 판별 프로세서에 위임하는 역할만 수행함.
 */
class SMSReceiver : BroadcastReceiver() {

    // [DIP] 구체적인 탐지기가 아닌 인터페이스의 집합체인 PhishingProcessor 추상화에 의존함.
    private val processor = PhishingProcessor.createDefault()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        // [SRP] 어르신용 빌드일 때만 탐지 로직 가동
        if (com.angae.phishingdefender.BuildConfig.FLAVOR != "elderly") return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            val sender = messages[0].displayOriginatingAddress ?: "Unknown"
            val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

            val domainMessage = SmsMessage(sender = sender, body = fullBody)

            // [DIP] 실시간 URL 검사를 위해 비동기 프로세서 호출
            scope.launch {
                val result = processor.processAsync(domainMessage)
                if (result.isPhishing) {
                    startAlertActivity(context, domainMessage, result)
                }
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
            putExtra("guidance", result.guidance)
            putStringArrayListExtra("matched", ArrayList(result.matched))
        }
        context.startActivity(intent)
    }
}
