package com.angae.phishingdefender.domain.detector

import com.angae.phishingdefender.domain.model.SmsMessage

/**
 * [SRP] 탐지 결과 데이터를 담는 순수 데이터 모델.
 */
data class DetectionResult(
    val isPhishing: Boolean,
    val reason: String = "",
    val matched: List<String> = emptyList()
)

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
}
