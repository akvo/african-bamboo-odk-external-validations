package org.akvo.afribamodkvalidator.data.network

import kotlinx.serialization.json.JsonObject
import org.akvo.afribamodkvalidator.data.dto.KoboDataResponse
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
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

    @GET("api/v2/assets/{assetUid}/data.json")
    suspend fun getSubmissionsWithFields(
        @Path("assetUid") assetUid: String,
        @Query("query") query: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
        @Query("start") start: Int = 0
    ): KoboDataResponse

    /**
     * Bulk update submissions to write the dcu_validation_warnings field.
     * Primary strategy for persisting warnings back to Kobo.
     *
     * Payload example:
     * {"payload": {"submission_ids": [757910942], "data": {"dcu_validation_warnings": "..."}}}
     */
    @PATCH("api/v2/assets/{assetUid}/data/bulk/")
    suspend fun patchSubmissionsBulk(
        @Path("assetUid") assetUid: String,
        @Body payload: JsonObject
    ): JsonObject

    /**
     * POST a note to a submission via the v1 API.
     * Fallback strategy for persisting warnings (deprecated June 2026).
     *
     * Note format: "[DCU Warning] <human-readable message>"
     */
    @FormUrlEncoded
    @POST("api/v1/notes")
    suspend fun addNote(
        @Field("note") note: String,
        @Field("instance") instance: String
    ): JsonObject

    /**
     * Fetch asset detail to discover form structure (survey fields, choices).
     * Used to dynamically discover geoshape/geotrace polygon field paths.
     */
    @GET("api/v2/assets/{assetUid}/")
    suspend fun getAssetDetail(
        @Path("assetUid") assetUid: String,
        @Query("format") format: String = "json"
    ): JsonObject

    companion object {
        const val BASE_URL = "https://kf.kobotoolbox.org/"
        const val DEFAULT_PAGE_SIZE = 300
    }
}