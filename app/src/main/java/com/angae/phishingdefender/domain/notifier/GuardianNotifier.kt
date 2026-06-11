package com.angae.phishingdefender.domain.notifier

/**
 * [DIP] 보호자 알림 정책의 추상화.
 * 상세 구현(서버 전송, FCM 등)은 데이터 레이어에서 담당하며, 도메인은 이 규격만 알고 있음.
 */
interface GuardianNotifier {
    /**
     * @return Result<Unit> 전송 성공 여부 및 에러를 캡슐화하여 반환
     */
    suspend fun notifyGuardian(
        sender: String,
        body: String,
        reason: String
    ): Result<Unit>
}
