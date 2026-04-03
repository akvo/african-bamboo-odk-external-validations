package org.akvo.afribamodkvalidator.data.network

import org.akvo.afribamodkvalidator.data.dto.GitHubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApiService {

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubReleaseDto

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}
