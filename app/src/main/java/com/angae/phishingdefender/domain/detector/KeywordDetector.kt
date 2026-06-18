package com.angae.phishingdefender.domain.detector

import com.angae.phishingdefender.domain.model.SmsMessage

/**
 * [OCP] 키워드 기반 피싱 탐지기.
 * 생성자를 통해 키워드 목록을 주입받음으로써, 기존 코드를 수정하지 않고도 탐지 대상을 확장할 수 있음.
 */
class KeywordDetector : PhishingDetector {
    override val name: String = "키워드 분석 엔진"

    override fun detect(message: SmsMessage): DetectionResult {
        val body = message.body

        return when {
            // 1. 공공기관 사칭 (검찰, 경찰, 국세청 등)
            containsAny(body, "검찰", "경찰", "지방검찰청", "과태료", "미납", "출석", "수사") -> {
                DetectionResult(
                    isPhishing = true,
                    type = PhishingType.GOVERNMENT,
                    reason = "정부 기관 사칭 의심",
                    guidance = "정부 기관은 어떠한 경우에도 문자로 금전을 요구하거나 앱 설치를 권유하지 않습니다. 해당 번호를 차단하고 112에 신고하세요.",
                    matched = listOf("공공기관 사칭 키워드 발견")
                )
            }
            // 2. 금융기관 사칭 (카드, 은행, 대출)
            containsAny(body, "해외결제", "승인완료", "카드발급", "금전지원", "무이자", "연체", "정지") -> {
                DetectionResult(
                    isPhishing = true,
                    type = PhishingType.FINANCE,
                    reason = "금융 서비스 사칭 의심",
                    guidance = "본인이 요청하지 않은 결제 문자는 사기일 가능성이 매우 높습니다. 링크를 클릭하지 마시고 해당 카드사 고객센터로 직접 전화하여 확인하세요.",
                    matched = listOf("금융 거래 사칭 키워드 발견")
                )
            }
            // 3. 배송 사칭 (우체국, 로켓배송 등)
            containsAny(body, "택배", "배송", "주소지", "미확인", "반송", "통관", "송장") -> {
                DetectionResult(
                    isPhishing = true,
                    type = PhishingType.DELIVERY,
                    reason = "택배 배송 서비스 사칭 의심",
                    guidance = "택배 주소지 변경이나 재배송을 위해 별도의 링크 접속을 요구하는 것은 전형적인 수법입니다. 공식 앱이나 홈페이지를 이용하세요.",
                    matched = listOf("배송 관련 의심 키워드 발견")
                )
            }
            // 4. 지인 사칭 (아들, 딸, 휴대폰 고장)
            containsAny(body, "아들", "딸", "폰고장", "수리비", "인증", "부탁", "엄마", "아빠") -> {
                DetectionResult(
                    isPhishing = true,
                    type = PhishingType.FAMILY,
                    reason = "가족/지인 사칭 의심",
                    guidance = "가족을 사칭하여 금전이나 신분증 사진을 요구하는 수법입니다. 반드시 전화를 걸어 본인 목소리를 확인하기 전까지는 절대 응하지 마세요.",
                    matched = listOf("가족 사칭 의심 키워드 발견")
                )
            }
            else -> DetectionResult(isPhishing = false)
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }
}
