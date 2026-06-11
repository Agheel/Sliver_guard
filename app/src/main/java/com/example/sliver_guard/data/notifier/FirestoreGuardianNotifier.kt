package com.example.sliver_guard.data.notifier

import com.example.sliver_guard.domain.model.SmsMessage
import com.example.sliver_guard.domain.notifier.GuardianNotifier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DIP] 도메인 계층의 GuardianNotifier 인터페이스를 Firestore를 사용하여 구현함.
 * 고수준 모듈이 저수준 모듈(Firestore)의 상세 구현에 의존하지 않도록 인터페이스를 통해 역전시킴.
 */
class FirestoreGuardianNotifier(
    private val firestore: FirebaseFirestore
) : GuardianNotifier {

    override suspend fun notifyGuardian(
        message: SmsMessage,
        reason: String,
        elderId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Firestore에 저장할 데이터 맵 구성
            val alertData = hashMapOf(
                "elderId" to elderId,
                "sender" to message.sender,
                "body" to message.body,
                "reason" to reason,
                "createdAt" to FieldValue.serverTimestamp() // 서버 시간 기준 저장
            )

            // "alerts" 컬렉션에 문서 추가 후 .await()으로 비동기 대기
            firestore.collection("alerts")
                .add(alertData)
                .await()

            true
        } catch (e: Exception) {
            // 예외 발생 시 에러 로그 출력 및 false 반환
            e.printStackTrace()
            false
        }
    }
}
