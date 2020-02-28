package io.ktor.web.plugins

import io.ktor.http.*
import io.ktor.web.plugins.model.*
import java.io.*

fun collectModel(configDir: File = File("plugins.d")): Pair<AppModel, ValidationResult> {
    val allPlugins = collectPlugins(configDir).toList()
    val validationResult = validate(allPlugins)

    val featured = listOf(allPlugins.first())

    return Pair(AppModel(allPlugins, featured, validationResult, Parameters.Empty), validationResult)
}