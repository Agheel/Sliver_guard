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
        return mergeResults(results)
    }

    /**
     * 비동기 탐지기(URL 실시간 검사 등)를 포함하여 결과를 취합함.
     */
    suspend fun processAsync(message: SmsMessage): DetectionResult {
        val results = detectors.map { it.detectAsync(message) }
        return mergeResults(results)
    }

    private fun mergeResults(results: List<DetectionResult>): DetectionResult {
        val phishingResults = results.filter { it.isPhishing }
        val isAnyPhishing = phishingResults.isNotEmpty()
        
        // 가장 구체적인 결과(Enum 순서상 NONE이 아닌 것)를 우선 선택
        val primaryResult = phishingResults.firstOrNull { it.type != PhishingType.NONE } 
            ?: phishingResults.firstOrNull() 
            ?: DetectionResult(isPhishing = false)

        return DetectionResult(
            isPhishing = isAnyPhishing,
            type = primaryResult.type,
            reason = phishingResults.joinToString(" / ") { it.reason }.ifEmpty { "정상 메시지" },
            guidance = primaryResult.guidance.ifEmpty { 
                if (isAnyPhishing) "출처가 불분명한 링크는 절대 클릭하지 마시고, 즉시 삭제하시기 바랍니다." else ""
            },
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
