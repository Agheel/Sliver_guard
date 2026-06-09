package com.angae.phishingdefender.domain.detector

import com.angae.phishingdefender.domain.model.SmsMessage

/**
 * [OCP] 키워드 기반 피싱 탐지기.
 * 생성자를 통해 키워드 목록을 주입받음으로써, 기존 코드를 수정하지 않고도 탐지 대상을 확장할 수 있음.
 */
class KeywordDetector(
    private val phishingKeywords: List<String> = DEFAULT_KEYWORDS
) : PhishingDetector {
    override val name: String = "키워드 탐지기"

    override fun detect(message: SmsMessage): DetectionResult {
        val matchedKeywords = phishingKeywords.filter { keyword ->
            message.body.contains(keyword, ignoreCase = true)
        }

        return DetectionResult(
            isPhishing = matchedKeywords.isNotEmpty(),
            reason = if (matchedKeywords.isNotEmpty()) "피싱 의심 단어 포함" else "",
            matched = matchedKeywords
        )
    }

    companion object {
        val DEFAULT_KEYWORDS = listOf(
            "국제발신", "해외결제", "통관지연", "검찰청", "우체국본인확인",
            "금전지원", "무이자대출", "연체", "정지", "카드발급"
        )
    }
}
