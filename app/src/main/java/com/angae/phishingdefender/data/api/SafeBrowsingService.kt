package com.angae.phishingdefender.data.api

import com.angae.phishingdefender.data.api.model.ClientInfo
import com.angae.phishingdefender.data.api.model.SafeBrowsingRequest
import com.angae.phishingdefender.data.api.model.ThreatEntry
import com.angae.phishingdefender.data.api.model.ThreatInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * [SRP] 실제 네트워크 통신을 통해 URL/도메인의 유해성을 실시간으로 검사함.
 * Google Safe Browsing API를 사용하여 실시간 유해성을 판단함.
 */
class SafeBrowsingService {

    private val apiKey = com.angae.phishingdefender.BuildConfig.SAFE_BROWSING_API_KEY

    private val api = Retrofit.Builder()
        .baseUrl("https://safebrowsing.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GoogleSafeBrowsingApi::class.java)

    /**
     * URL의 유해성을 Google Safe Browsing API를 통해 실시간으로 분석함.
     */
    suspend fun isUrlMalicious(url: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey.contains("YOUR_ACTUAL_API_KEY")) {
            // API 키가 비어있거나 기본값인 경우 기존 DNS 로직으로 폴백
            return@withContext fallbackDnsCheck(url)
        }

        try {
            val request = SafeBrowsingRequest(
                client = ClientInfo(clientId = "sliver-guard", clientVersion = "1.0.0"),
                threatInfo = ThreatInfo(
                    threatTypes = listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFFUL_APPLICATION"),
                    platformTypes = listOf("ANY_PLATFORM"),
                    threatEntryTypes = listOf("URL"),
                    threatEntries = listOf(ThreatEntry(url))
                )
            )

            val response = api.findThreatMatches(apiKey, request)
            return@withContext !response.matches.isNullOrEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            // 네트워크 오류 시 기존 로직으로 폴백
            fallbackDnsCheck(url)
        }
    }

    /**
     * API 키가 없거나 네트워크 오류 시 사용하는 기존 DNS 기반 체크 로직
     */
    private fun fallbackDnsCheck(url: String): Boolean {
        return try {
            val domain = extractDomain(url) ?: return false
            val isSuspiciousPattern = domain.contains("phishing") || 
                                     domain.contains("verify") || 
                                     domain.contains("account")
            isSuspiciousPattern
        } catch (e: Exception) {
            false
        }
    }

    private fun extractDomain(url: String): String? {
        return try {
            val javaUrl = if (!url.startsWith("http")) java.net.URL("http://$url") else java.net.URL(url)
            javaUrl.host
        } catch (e: Exception) {
            null
        }
    }
}
