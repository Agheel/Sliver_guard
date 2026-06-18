package com.angae.phishingdefender.data.api.model

data class SafeBrowsingRequest(
    val client: ClientInfo,
    val threatInfo: ThreatInfo
)

data class ClientInfo(
    val clientId: String,
    val clientVersion: String
)

data class ThreatInfo(
    val threatTypes: List<String>,
    val platformTypes: List<String>,
    val threatEntryTypes: List<String>,
    val threatEntries: List<ThreatEntry>
)

data class ThreatEntry(
    val url: String
)

data class SafeBrowsingResponse(
    val matches: List<ThreatMatch>?
)

data class ThreatMatch(
    val threatType: String,
    val platformType: String,
    val threatEntryType: String,
    val threat: ThreatEntry,
    val threatEntries: List<Map<String, String>>?,
    val cacheDuration: String?
)
