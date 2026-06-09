package com.angae.phishingdefender.domain.notifier

/**
 * [DIP] 보호자에게 알림을 보내는 고수준 정책 인터페이스.
 * 실제 FCM 발송이나 SMS 발송 등의 상세 구현은 이 인터페이스를 구현하는 클래스에서 담당함.
 */
interface GuardianNotifier {
    /**
     * @param sender 피싱 문자 발신자
     * @param body 피싱 문자 내용
     * @param reason 탐지 사유
     */
    fun notifyGuardian(sender: String, body: String, reason: String)
}
