package io.ktor.web.plugins.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// see Validation.kt when adding new fields

@Serializable
data class PluginDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val webSite: String,
    val versionsMappings: List<VersionsMapping>,
    val deprecated: Boolean
) {
    @Transient
    var filePath: String? = null
}

@Serializable
data class VersionsMapping(
    val ktorVersionRange: String,
    val coordinates: String
)
