package io.ktor.web.plugins.pages

import kotlinx.html.*
import kotlinx.html.stream.*

@Deprecated("")
fun buildPage(title: String, body: BODY.() -> Unit): String = createHTML().html {
    pageTemplate(title, body)
}

fun HTML.pageTemplate(title: String, body: BODY.() -> Unit) {
    head {
        title(title)
        meta(charset = "utf-8")
    }

    body {
        body()

        ktorFooter()
    }
}

private fun BODY.ktorFooter() {
    div {
        p {
            text("Ktor plugins portal version 0.0.1.")
        }
    }
}