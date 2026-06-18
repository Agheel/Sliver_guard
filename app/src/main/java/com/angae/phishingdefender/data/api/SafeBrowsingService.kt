package com.angae.phishingdefender.data.api

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SRP] Google Safe Browsing API를 사용하여 URL의 유해성을 검사하는 서비스.
 * API 키가 없는 경우 시연을 위해 일부 URL에 대해 피싱으로 판정하는 시뮬레이션 로직을 포함함.
 */
class SafeBrowsingService {

    // 실습/시연용으로 API 키가 없을 경우를 대비한 가상 검사 로직
    private val fakeBlacklist = listOf("malware-site.com", "phishing-test.kr", "login-verify.net")

    suspend fun isUrlMalicious(url: String): Boolean = withContext(Dispatchers.IO) {
        // 1. 시연용 로컬 블랙리스트 체크 (API 호출 전 우선 확인)
        if (fakeBlacklist.any { url.contains(it) }) return@withContext true

        // 2. 실제 API 호출 (API 키가 설정된 경우에만 동작하도록 설계 가능)
        // 여기서는 기술적 구조를 보여주기 위해 골격만 유지하고 기본 false 반환
        // 실제 운영 시에는 아래와 같은 REST API 호출 로직이 들어감
        return@withContext false
    }

    /**
     * 참고: 실제 Google Safe Browsing Lookup API 호출 예시 (구현 방식 참조용)
     */
    private fun checkViaApi(urlToCheck: String, apiKey: String): Boolean {
        try {
            val url = URL("https://safebrowsing.googleapis.com/v4/threatMatches:find?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val requestJson = JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientId", "sliver-guard")
                    put("clientVersion", "1.0.0")
                })
                put("threatInfo", JSONObject().apply {
                    put("threatTypes", JSONArray(listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE")))
                    put("platformTypes", JSONArray(listOf("ANY_PLATFORM")))
                    put("threatEntryTypes", JSONArray(listOf("URL")))
                    put("threatEntries", JSONArray(listOf(JSONObject().apply { put("url", urlToCheck) })))
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(requestJson.toString()) }
            
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            return response.contains("matches") // 결과에 matches가 있으면 유해함
        } catch (e: Exception) {
            return false
        }
    }
}
