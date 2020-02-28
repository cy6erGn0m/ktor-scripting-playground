package io.ktor.web.plugins.pages

import io.ktor.web.plugins.model.*
import kotlinx.html.*

@Deprecated("")
fun buildMainPage(
    model: AppModel
): String = buildPage("Ktor plugin portal") {
    h1 { text("Ktor plugin portal") }

    if (model.validationResult != null) {
        div {
            p {
                text("Development mode. ")

                if (model.validationResult.hasIssues) {
                    text("There are issues. ")
                }

                text("See ")
                a(href = "/dev") {
                    text("dev")
                }
                text(" page for details.")
            }
        }
    }

    div {
        h2 { text("Featured plugins") }

        model.featuredPlugins.forEach { plugin ->
            p {
                a(href = WebSite.pluginPage(plugin)) {
                    text(plugin.title)
                }
            }
        }
    }
}
