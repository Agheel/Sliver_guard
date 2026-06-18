package com.angae.phishingdefender.domain.detector

import com.angae.phishingdefender.domain.model.SmsMessage

/**
 * [SRP] 탐지 결과 데이터를 담는 순수 데이터 모델.
 */
data class DetectionResult(
    val isPhishing: Boolean,
    val type: PhishingType = PhishingType.NONE,
    val reason: String = "",
    val guidance: String = "",
    val matched: List<String> = emptyList()
)

enum class PhishingType(val title: String) {
    NONE("정상"),
    GOVERNMENT("공공기관 사칭"),
    FINANCE("금융/은행 사칭"),
    DELIVERY("택배/배송 사칭"),
    FAMILY("지인/가족 사칭"),
    UNKNOWN("알 수 없는 위협")
}

/**
 * [DIP / ISP] 피싱 판별을 위한 범용 인터페이스.
 * 인터페이스는 오직 탐지 기능에만 집중하여 ISP를 준수함.
 */
interface PhishingDetector {
    val name: String
    
    /**
     * @return DetectionResult 탐지 결과 (피싱 여부, 사유, 매칭된 데이터)
     */
    fun detect(message: SmsMessage): DetectionResult

    /**
     * 네트워크 통신이 필요한 심화 탐지 (선택 사항)
     */
    suspend fun detectAsync(message: SmsMessage): DetectionResult = detect(message)
}
