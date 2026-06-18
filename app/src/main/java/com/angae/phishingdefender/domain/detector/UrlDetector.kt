package com.angae.phishingdefender.domain.detector

import com.angae.phishingdefender.data.api.SafeBrowsingService
import com.angae.phishingdefender.domain.model.SmsMessage
import java.util.regex.Pattern

/**
 * [OCP] URL 기반 피싱 탐지기.
 * 정규식을 통해 URL을 추출하고 단축 URL, IP 호스트, 비보안(http) 연결 등을 탐지함.
 */
class UrlDetector : PhishingDetector {
    override val name: String = "URL 탐지기"
    private val safeBrowsingService = SafeBrowsingService()

    private val urlPattern = Pattern.compile(
        "(http|https)://[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,}(/\\S*)?"
    )

    // ... (기존 패턴들)

    override fun detect(message: SmsMessage): DetectionResult {
        // 기존 동기 탐지 로직 (패턴 기반)
        val matcher = urlPattern.matcher(message.body)
        val matchedUrls = mutableListOf<String>()
        val suspiciousUrls = mutableListOf<String>()

        while (matcher.find()) {
            val url = matcher.group()
            matchedUrls.add(url)

            if (isPatternSuspicious(url)) {
                suspiciousUrls.add(url)
            }
        }

        return buildResult(suspiciousUrls, matchedUrls)
    }

    override suspend fun detectAsync(message: SmsMessage): DetectionResult {
        val matcher = urlPattern.matcher(message.body)
        val matchedUrls = mutableListOf<String>()
        val suspiciousUrls = mutableListOf<String>()

        while (matcher.find()) {
            val url = matcher.group()
            matchedUrls.add(url)

            // 1. 기존 패턴 기반 검사
            if (isPatternSuspicious(url)) {
                suspiciousUrls.add(url)
                continue
            }

            // 2. [추가] 실시간 URL 유해성 검사 (Threat Intelligence 연동)
            if (safeBrowsingService.isUrlMalicious(url)) {
                suspiciousUrls.add("$url (실시간 블랙리스트 탐지)")
            }
        }

        return buildResult(suspiciousUrls, matchedUrls)
    }

    private fun isPatternSuspicious(url: String): Boolean {
        val isInsecure = url.startsWith("http://")
        val isShortened = shortenedUrlPattern.matcher(url).matches()
        val isIpHost = ipHostPattern.matcher(url).matches()
        return isInsecure || isShortened || isIpHost
    }

    private fun buildResult(suspiciousUrls: List<String>, matchedUrls: List<String>): DetectionResult {
        val isPhishing = suspiciousUrls.isNotEmpty()
        val reason = if (isPhishing) {
            val reasons = mutableListOf<String>()
            if (suspiciousUrls.any { it.contains("http://") }) reasons.add("비보안 연결")
            if (suspiciousUrls.any { it.contains("실시간") }) reasons.add("실시간 블랙리스트 매칭")
            if (suspiciousUrls.any { shortenedUrlPattern.matcher(it).matches() }) reasons.add("단축 URL")
            "위험 URL 발견: ${reasons.joinToString(", ")}"
        } else ""

        return DetectionResult(
            isPhishing = isPhishing,
            type = if (isPhishing) PhishingType.UNKNOWN else PhishingType.NONE,
            reason = reason,
            matched = suspiciousUrls.ifEmpty { matchedUrls }
        )
    }

    private val shortenedUrlPattern = Pattern.compile(
        ".*(bit\\.ly|tinyurl\\.com|t\\.co|goo\\.gl|me2\\.do|kakaotalk\\.it).*",
        Pattern.CASE_INSENSITIVE
    )
    private val ipHostPattern = Pattern.compile(
        "^https?://(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?:/.*)?$"
    )
}
