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
import com.angae.phishingdefender.ui.alert.AlertActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * [SRP] 서버로부터 온 푸시 알림을 처리하고 시스템 알림으로 표시하는 책임만 수행함.
 */
class PhishingFcmService : FirebaseMessagingService() {

    private var simulationListener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        // [추가] 백그라운드에서도 시뮬레이션 명령(테스트 모드)을 감시함
        startObservingSimulations()
    }

    override fun onDestroy() {
        super.onDestroy()
        simulationListener?.remove()
    }

    private fun startObservingSimulations() {
        if (com.angae.phishingdefender.BuildConfig.FLAVOR != "elderly") return

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val hash = Math.abs(deviceId.hashCode())
        val myId = (hash % 1000000).toString().padStart(6, '0')

        simulationListener = FirebaseFirestore.getInstance().collection("simulations")
            .whereEqualTo("elderCode", myId)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val sender = dc.document.getString("sender") ?: "Unknown"
                        val body = dc.document.getString("body") ?: ""
                        val reason = dc.document.getString("reason") ?: "테스트 탐지"

                        // 즉시 문서 삭제 (중복 방지)
                        dc.document.reference.delete()

                        // 백그라운드에서도 즉시 경고 화면 실행
                        val intent = Intent(this, AlertActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("sender", sender)
                            putExtra("body", body)
                            putExtra("reason", reason)
                            putExtra("guidance", "이것은 가디언 앱에서 보낸 테스트용 메시지입니다.")
                            putStringArrayListExtra("matched", arrayListOf(sender))
                        }
                        startActivity(intent)
                    }
                }
            }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        updateTokenOnFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (com.angae.phishingdefender.BuildConfig.FLAVOR != "guardian") return

        val title = remoteMessage.data["title"] ?: "🚨 긴급! 어르신 피싱 위협 감지"
        val message = remoteMessage.data["message"] ?: "어르신이 피싱 문자를 받았습니다!"
        
        showNotification(title, message)
    }

    private fun updateTokenOnFirestore(token: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val role = com.angae.phishingdefender.BuildConfig.FLAVOR
        
        val hash = Math.abs(deviceId.hashCode())
        val elderCode = (hash % 1000000).toString().padStart(6, '0')

        val deviceData = hashMapOf(
            "fcmToken" to token,
            "role" to role,
            "elderCode" to elderCode,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val docId = "${deviceId}_$role"
        FirebaseFirestore.getInstance().collection("devices").document(docId)
            .set(deviceData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FCM_SERVICE", "FCM 토큰 서버 업데이트 성공: $token")
            }
            .addOnFailureListener { e ->
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
            putExtra("from_fcm", true)
            putExtra("fcm_title", title)
            putExtra("fcm_message", message)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
