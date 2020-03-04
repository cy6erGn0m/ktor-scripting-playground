package io.ktor.web.plugins

import io.ktor.web.plugins.model.*
import java.net.*

fun validate(plugins: List<PluginDescriptor>): ValidationResult {
    val result = ValidationResult()

    plugins.forEach { plugin ->
        check(plugin.filePath != null)
        plugin.validate(result)
    }

    plugins.validateDuplicates(result)

    return result
}

private fun List<PluginDescriptor>.validateDuplicates(validationResult: ValidationResult) {
    val duplicates = findDuplicatesBy { it.id.toLowerCase() } +
            findDuplicatesBy { it.title.trim().toLowerCase() }

    duplicates.forEach { row ->
        validationResult.error(row.first(), "Found duplicate plugins: ${row.joinToString { it.id }}")
    }
}

private fun List<PluginDescriptor>.findDuplicatesBy(
    discriminator: (PluginDescriptor) -> String
): List<List<PluginDescriptor>> {
    return groupBy(discriminator).filterValues { it.size > 1 }.values.toList()
}

private fun PluginDescriptor.validate(validationResult: ValidationResult) {
    validatePluginId(validationResult)
    validateTitle(validationResult)
    validateDescription(validationResult)
    validateWebSite(validationResult)
    validateTags(validationResult)

    versionsMappings.forEach { version ->
        version.validateMapping(this, validationResult)
    }

    if (versionsMappings.isEmpty()) {
        validationResult.error(this, "No version mappings provided.")
    }
}

private fun PluginDescriptor.validatePluginId(validationResult: ValidationResult) {
    if (id.isEmpty()) {
        validationResult.error(this, "id should be at least 4 characters long.")
        return
    }

    if (id.length < 4) {
        validationResult.error(this, "id '$id' is too short: at least 4 required")
    }

    if (!id.matches("[a-zA-Z][a-zA-Z0-9_.:+*-]*".toRegex())) {
        validationResult.error(
            this, "id '$id' is not valid. Only latin letters, " +
                    "digits and the following characters are allowed: _ . : + * -."
        )
    }

    if (id in listOf("example")) {
        if (filePath != "example.json") {
            validationResult.error(this, "id $id is resrved.")
        }
    }
}

private fun PluginDescriptor.validateTitle(validationResult: ValidationResult) {
    if (title.isBlank()) {
        validationResult.error(this, "title shouldn't be blank.")
        return
    }

    if (title.trim() != title) {
        validationResult.warning(this, "title contains leading or trailing whitespaces.")
    }

    if (title.trim().length > 30) {
        validationResult.error(this, "title is too long: at most 30 characters allowed.")
    }
    if (title.trim().length < 4) {
        validationResult.error(this, "title is too short: at least 4 characters required.")
    }

    if (title.any { it < ' ' }) {
        validationResult.error(this, "Control characters in title are not allowed.")
    }
}

private fun PluginDescriptor.validateDescription(validationResult: ValidationResult) {
    if (description.trim() != description) {
        validationResult.warning(this, "description contains leading or trailing whitespaces.")
    }
    if (description.any { it < ' ' && it != '\n' && it != '\r' }) {
        validationResult.error(
            this,
            "Control characters in description are not allowed except for line feed."
        )
    }
}

private fun PluginDescriptor.validateWebSite(validationResult: ValidationResult) {
    val webSite = webSite.trim()

    if (webSite != this.webSite) {
        validationResult.warning(this, "webSite contains leading or trailing whitespaces.")
    }

    if (':' in webSite) {
        if (!webSite.startsWith("https://")) {
            validationResult.error(this, "Website should use https.")
        }
    } else {
        validationResult.warning(this, "webSite url should contain explicit https protocol specification.")
    }

    val url = try {
        URL(webSite)
    } catch (e: Throwable) {
        validationResult.error(this, "webSite url is not valid: ${e.message}")
        null
    }

    if (url != null) {
        if (!url.query.isNullOrEmpty()) {
            validationResult.warning(this, "webSite query parameters are not recommended.")
        }

        try {
            if (InetAddress.getAllByName(url.host).isNullOrEmpty()) {
                validationResult.error(this, "webSite host has no DNS entries.")
            }
        } catch (t: Throwable) {
            validationResult.error(this, "webSite host is not valid: $t")
        }

        if (url.userInfo != null) {
            validationResult.error(this, "webSite should have no user:pass specification.")
        }
    }
}

private val VALID_TAG_CHARACTERS = (('a' .. 'z') + ('A' .. 'Z') + ('0' .. '9') + '-').toSet()

private fun PluginDescriptor.validateTags(validationResult: ValidationResult) {
    if (tags.isEmpty()) {
        validationResult.error(
            this, "No tags specified for plugin $id"
        )
    }

    tags.forEach { tag ->
        if (tag.trim() != tag) {
            validationResult.error(this, "Trailing or leading spaces in tag '$tag'")
        }

        if (!tag.all { it in VALID_TAG_CHARACTERS}) {
            validationResult.error(this,
                "Only latin letters, digits and minus sign are allowed in tag, got tag '$tag'")
        }
    }
}

private fun VersionsMapping.validateMapping(pluginDescriptor: PluginDescriptor, validationResult: ValidationResult) {
    val coordinates = coordinates.trim()
    if (coordinates != this.coordinates) {
        validationResult.warning(
            pluginDescriptor,
            "Maven coordinates '${this.coordinates}' should have no trailing or leading spaces."
        )
    }

    if (!coordinates.matches("[a-zA-Z_.-]+:[a-zA-Z_.-]+:[\\[(]?[0-9a-zA-Z_,.-]*[])+]?".toRegex())) {
        validationResult.error(pluginDescriptor, "Maven coordinates are illegal: '$coordinates'")
        return
    }

    val (group, artifactId, version) = coordinates.split(":")
    check(group.isNotEmpty())
    check(artifactId.isNotEmpty())

    if (version.first().isRangeStart() || version.last().isRangeEnd()) {
        if (!version.first().isRangeStart() || !version.last().isRangeEnd()) {
            validationResult.error(pluginDescriptor, "Maven version has illegal range: '$version'")
        }

        if (version.count { it == ',' } != 1) {
            validationResult.error(pluginDescriptor, "Maven range version should have a single comma.")
        }

        val rangeStart = version.drop(1).substringBefore(',')
        val rangeEnd = version.dropLast(1).substringAfter(',')

        validateVersion(rangeStart, pluginDescriptor, validationResult, allowEmpty = true)
        validateVersion(rangeEnd, pluginDescriptor, validationResult, allowEmpty = true)
    } else if (version.endsWith("+")) {
        validateVersion(version.dropLast(1), pluginDescriptor, validationResult, allowEmpty = true)
    } else {
        validateVersion(version, pluginDescriptor, validationResult, allowEmpty = false)
    }
}

private fun validateVersion(
    version: String,
    pluginDescriptor: PluginDescriptor,
    validationResult: ValidationResult,
    allowEmpty: Boolean
) {
    if (!allowEmpty && version.isEmpty()) {
        validationResult.error(pluginDescriptor, "Empty version")
        return
    }

    version.split('-', '_').forEach { part ->
        if (part.isEmpty()) {
            validationResult.warning(pluginDescriptor, "Empty version component in '$version'")
        }
    }
}

private fun Char.isRangeStart() = this == '[' || this == '('
private fun Char.isRangeEnd() = this == ']' || this == ')'
