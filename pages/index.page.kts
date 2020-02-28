pageTemplate("Ktor plugin portal") {
    h1 { text("Ktor plugin portal") }

    model.validationResult?.let { validation ->
        div {
            p {
                text("Development mode. ")

                if (validation.hasIssues) {
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
