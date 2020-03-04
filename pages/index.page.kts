pageTemplate("Ktor plugin portal") {
    h1 { text("Ktor plugin portal") }

    model.validationResult?.let { validation ->
        div {
            p {
                text("Development mode. ")

                if (validation.hasIssues) {
                    text("There are ${validation.collected.size} issue(s).  ")
                }

                text("See ")
                a(href = "/dev.html") {
                    text("dev")
                }
                text(" page for details.")
            }
        }
    }

    div {
        p {
            text("Tags:")
        }
        p {
            model.tags.forEach { (tag, _) ->
                span {
                    a(href = "/plugins/tag/$tag.html") {
                        text(tag)
                    }
                }
                text(" ")
            }
        }
    }

    div {
        h2 { text("Featured plugins") }

        table {
            thead {
                tr {
                    th {
                        text("Title")
                    }
                }
            }
            tbody {
                model.featuredPlugins.forEach { plugin ->
                    tr {
                        td {
                            a(href = WebSite.pluginPage(plugin)) {
                                text(plugin.title)
                            }
                        }
                    }
                }
            }
        }
    }
}
