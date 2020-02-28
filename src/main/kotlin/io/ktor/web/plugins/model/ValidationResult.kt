package io.ktor.web.plugins.model

import kotlinx.serialization.Serializable

@Serializable
class ValidationResult {
    private val issues = ArrayList<Issue>()

    @Serializable
    data class Issue(val descriptor: PluginDescriptor, val text: String, val isError: Boolean)

    val collected: List<Issue> get() = issues.toList()

    val hasErrors: Boolean
        get() = issues.any { it.isError }

    val hasIssues: Boolean
        get() = issues.isNotEmpty()

    fun warning(descriptor: PluginDescriptor, text: String) {
        issues.add(Issue(descriptor, text, isError = false))
    }

    fun error(descriptor: PluginDescriptor, text: String) {
        issues.add(Issue(descriptor, text, isError = true))
    }
}