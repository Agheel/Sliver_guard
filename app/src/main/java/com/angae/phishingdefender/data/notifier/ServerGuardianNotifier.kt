package com.angae.phishingdefender.data.notifier

import com.angae.phishingdefender.domain.notifier.GuardianNotifier
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [OCP/DIP] 서버를 통해 보호자에게 알림을 보내는 구체적인 구현체.
 */
class ServerGuardianNotifier : GuardianNotifier {

    private val serverUrl = "http://YOUR_SERVER_IP:8000/api/v1/phishing-alert" 

    override suspend fun notifyGuardian(
        sender: String,
        body: String,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 본인의 FCM 토큰 획득
            val token = getFcmToken() ?: return@withContext Result.failure(Exception("FCM 토큰을 가져올 수 없습니다."))

            // 2. JSON 데이터 생성
            val jsonBody = JSONObject().apply {
                put("sender", sender)
                put("body", body)
                put("reason", reason)
                put("fcm_token", token)
            }.toString()

            // 3. HTTP 연결 설정
            val url = URL(serverUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            // 4. 데이터 전송
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            // 5. 응답 확인
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("서버 응답 오류: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Firebase Messaging 토큰을 코루틴 중단 함수(suspend function) 형태로 가져옴.
     * [Best Practice] suspendCancellableCoroutine을 사용하여 취소 가능성을 보장함.
     */
    private suspend fun getFcmToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (continuation.isActive) {
                if (task.isSuccessful) {
                    continuation.resume(task.result, onCancellation = null)
                } else {
                    continuation.resume(null, onCancellation = null)
                }
            }
        }
    }
}
