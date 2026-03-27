package org.akvo.afribamodkvalidator.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KoboAsset(
    @SerialName("uid")
    val uid: String,
    @SerialName("name")
    val name: String,
    @SerialName("deployment_status")
    val deploymentStatus: String? = null
)

@Serializable
data class KoboAssetsResponse(
    @SerialName("count")
    val count: Int,
    @SerialName("next")
    val next: String? = null,
    @SerialName("results")
    val results: List<KoboAsset>
)
