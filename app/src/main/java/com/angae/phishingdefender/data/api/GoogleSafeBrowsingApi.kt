package com.angae.phishingdefender.data.api

import com.angae.phishingdefender.data.api.model.SafeBrowsingRequest
import com.angae.phishingdefender.data.api.model.SafeBrowsingResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GoogleSafeBrowsingApi {
    @POST("v4/threatMatches:find")
    suspend fun findThreatMatches(
        @Query("key") apiKey: String,
        @Body request: SafeBrowsingRequest
    ): SafeBrowsingResponse
}
