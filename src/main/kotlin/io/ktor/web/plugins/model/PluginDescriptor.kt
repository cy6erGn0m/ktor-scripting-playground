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
    val deprecated: Boolean = false,
    val tags: List<String> = listOf()
) {
    @Transient
    var filePath: String? = null
}
