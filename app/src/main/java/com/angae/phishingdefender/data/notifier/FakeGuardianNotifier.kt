package com.angae.phishingdefender.data.notifier

import android.util.Log
import com.angae.phishingdefender.domain.notifier.GuardianNotifier

/**
 * [OCP] 테스트를 위한 가짜 알림 발송기.
 * 실제 백엔드가 없는 환경에서도 도메인 로직을 검증할 수 있게 함.
 */
class FakeGuardianNotifier : GuardianNotifier {
    override suspend fun notifyGuardian(
        sender: String,
        body: String,
        reason: String
    ): Result<Unit> {
        // [SRP] 실제 전송 대신 로그 출력만 담당
        Log.d("FakeGuardianNotifier", "보호자에게 알림 전송: 발신자=$sender, 이유=$reason")
        
        // TODO: 백엔드 완성 후 ServerGuardianNotifier로 교체
        return Result.success(Unit)
    }
}
