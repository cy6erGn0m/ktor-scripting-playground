package io.ktor.web.plugins.model

import io.ktor.http.*
import io.ktor.web.plugins.collectTags
import kotlinx.serialization.*

@Serializable
data class AppModel(
    val allPlugins: List<PluginDescriptor>,
    val featuredPlugins: List<PluginDescriptor>,
    val validationResult: ValidationResult?,
    @Transient
    val parameters: Parameters = Parameters.Empty
) {
    @Transient
    val byPluginId = allPlugins.associateBy { it.id }

    @Transient
    val tags: Map<String, List<PluginDescriptor>> = collectTags(allPlugins)
}
