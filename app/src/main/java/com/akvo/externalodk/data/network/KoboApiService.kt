package com.akvo.externalodk.data.network

import com.akvo.externalodk.data.dto.KoboDataResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface KoboApiService {

    @GET("api/v2/assets/{assetUid}/data.json")
    suspend fun getSubmissions(
        @Path("assetUid") assetUid: String,
        @Query("limit") limit: Int = 300,
        @Query("start") start: Int = 0
    ): KoboDataResponse

    @GET("api/v2/assets/{assetUid}/data.json")
    suspend fun getSubmissionsSince(
        @Path("assetUid") assetUid: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 300,
        @Query("start") start: Int = 0
    ): KoboDataResponse

    companion object {
        const val BASE_URL = "https://kf.kobotoolbox.org/"
        const val DEFAULT_PAGE_SIZE = 300
    }
}
