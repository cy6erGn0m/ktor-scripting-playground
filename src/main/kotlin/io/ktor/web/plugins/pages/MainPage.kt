package io.ktor.web.plugins.pages

import io.ktor.web.plugins.model.*
import io.ktor.web.plugins.scripting.Lookup
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlin.reflect.full.primaryConstructor

fun buildMainPage(
    model: AppModel
): String = buildString {
    createHTML().html {
        runBlocking {
            Lookup.scriptClassFor("index")?.pageClass?.primaryConstructor!!.call(model, this)
        }
    }
}
