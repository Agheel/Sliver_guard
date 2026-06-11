package com.angae.phishingdefender.data.notifier

import android.content.Context
import android.provider.Settings
import com.angae.phishingdefender.domain.notifier.GuardianNotifier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DIP/OCP] Firestore를 통해 피싱 알림 데이터를 서버로 전송하는 실제 구현체.
 * Firestore에 데이터가 생성되면 Cloud Functions가 이를 감지하여 보호자에게 푸시를 보냄.
 */
class FirestoreGuardianNotifier(private val context: Context) : GuardianNotifier {

    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun notifyGuardian(
        sender: String,
        body: String,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // [SRP] 기기 고유 ID를 elderId로 사용 (MainActivity의 등록 정보와 일치시킴)
            val elderId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            // 2. 전송할 데이터 맵 구성
            val alertData = hashMapOf(
                "elderId" to elderId,
                "sender" to sender,
                "body" to body,
                "reason" to reason,
                "createdAt" to FieldValue.serverTimestamp()
            )

            // 3. Firestore "alerts" 컬렉션에 새 문서 추가
            firestore.collection("alerts")
                .add(alertData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            // [Safety] 네트워크 에러 등이 발생해도 앱이 죽지 않도록 실패 결과 반환
            Result.failure(e)
        }
    }
}
