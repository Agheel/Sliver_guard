package com.angae.phishingdefender.data.notifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.angae.phishingdefender.ui.main.MainActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * [SRP] 서버로부터 온 푸시 알림을 처리하고 시스템 알림으로 표시하는 책임만 수행함.
 */
class PhishingFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // [SRP] 새 토큰을 서버에 등록하는 로직 (기기 식별 및 푸시 전송용)
        updateTokenOnFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // [SRP] 메시지 데이터 추출 및 알림 표시 위임
        val title = remoteMessage.data["title"] ?: "⚠️ 실버가드 긴급 알림"
        val message = remoteMessage.data["message"] ?: "어르신이 피싱 문자를 받았습니다!"
        
        showNotification(title, message)
    }

    private fun updateTokenOnFirestore(token: String) {
        // [SRP] 기기 고유 ID를 userId로 사용
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        // [SRP] SharedPreferences에서 사용자 역할(어르신/보호자) 가져오기
        val role = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("role", "elderly") ?: "elderly"

        val deviceData = hashMapOf(
            "fcmToken" to token,
            "role" to role,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // [SRP] Firestore의 devices 컬렉션에 토큰 정보 업데이트 (중복 방지를 위해 merge 사용)
        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
            .set(deviceData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FCM_SERVICE", "FCM 토큰 서버 업데이트 성공: $token")
            }
            .addOnFailureListener { e ->
                // [Safety] 실패해도 앱이 죽지 않도록 로깅만 수행
                Log.e("FCM_SERVICE", "FCM 토큰 서버 업데이트 실패", e)
            }
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "phishing_alert_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "피싱 경고 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "어르신의 피싱 문자 수신 시 보호자에게 알림을 보냅니다."
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
