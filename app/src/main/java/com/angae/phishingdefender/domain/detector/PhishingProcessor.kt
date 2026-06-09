package com.angae.phishingdefender.domain.detector

import com.angae.phishingdefender.domain.model.SmsMessage

/**
 * [DIP] 추상화된 PhishingDetector 인터페이스 리스트에만 의존하여 구체적인 탐지 로직으로부터 고수준 정책을 분리함.
 */
class PhishingProcessor(
    private val detectors: List<PhishingDetector>
) {
    /**
     * 모든 탐지기의 결과를 취합하여 최종 결과를 반환함.
     */
    fun process(message: SmsMessage): DetectionResult {
        val results = detectors.map { it.detect(message) }
        val isAnyPhishing = results.any { it.isPhishing }
        
        return DetectionResult(
            isPhishing = isAnyPhishing,
            reason = results.filter { it.isPhishing }
                .joinToString(", ") { it.reason }
                .ifEmpty { "정상 메시지" },
            matched = results.flatMap { it.matched }.distinct()
        )
    }

    companion object {
        /**
         * 기본 탐지기들을 조립하여 반환하는 팩토리 메서드.
         */
        fun createDefault(): PhishingProcessor {
            return PhishingProcessor(
                listOf(
                    KeywordDetector(),
                    UrlDetector()
                )
            )
        }
    }
}
