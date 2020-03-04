package io.ktor.web.plugins.model

import kotlinx.serialization.Serializable

@Serializable
data class VersionsMapping(
    val ktorVersionRange: String,
    val coordinates: String
)