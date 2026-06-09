package com.angae.phishingdefender.domain.detector

import com.angae.phishingdefender.domain.model.SmsMessage
import java.util.regex.Pattern

/**
 * [OCP] URL 기반 피싱 탐지기.
 * 정규식을 통해 URL을 추출하고 단축 URL, IP 호스트, 비보안(http) 연결 등을 탐지함.
 */
class UrlDetector : PhishingDetector {
    override val name: String = "URL 탐지기"

    private val urlPattern = Pattern.compile(
        "(http|https)://[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,}(/\\S*)?"
    )

    // 피싱 의심 패턴들
    private val shortenedUrlPattern = Pattern.compile(
        ".*(bit\\.ly|tinyurl\\.com|t\\.co|goo\\.gl|me2\\.do|kakaotalk\\.it).*",
        Pattern.CASE_INSENSITIVE
    )
    private val ipHostPattern = Pattern.compile(
        "^https?://(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?:/.*)?$"
    )

    override fun detect(message: SmsMessage): DetectionResult {
        val matcher = urlPattern.matcher(message.body)
        val matchedUrls = mutableListOf<String>()
        val suspiciousUrls = mutableListOf<String>()

        while (matcher.find()) {
            val url = matcher.group()
            matchedUrls.add(url)

            // 1. 비보안 http 프로토콜
            val isInsecure = url.startsWith("http://")
            // 2. 단축 URL 서비스 사용
            val isShortened = shortenedUrlPattern.matcher(url).matches()
            // 3. IP 주소 형식의 호스트
            val isIpHost = ipHostPattern.matcher(url).matches()

            if (isInsecure || isShortened || isIpHost) {
                suspiciousUrls.add(url)
            }
        }

        val isPhishing = suspiciousUrls.isNotEmpty()
        val reason = if (isPhishing) {
            val reasons = mutableListOf<String>()
            if (suspiciousUrls.any { it.startsWith("http://") }) reasons.add("비보안 연결(http)")
            if (suspiciousUrls.any { shortenedUrlPattern.matcher(it).matches() }) reasons.add("출처 불분명 단축 URL")
            if (suspiciousUrls.any { ipHostPattern.matcher(it).matches() }) reasons.add("IP 주소 링크")
            "위험 요소 발견: ${reasons.joinToString(", ")}"
        } else ""

        return DetectionResult(
            isPhishing = isPhishing,
            reason = reason,
            matched = suspiciousUrls.ifEmpty { matchedUrls }
        )
    }
}
