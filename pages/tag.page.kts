@file:PageRoute("/plugins/tag/{tag}.html")

val plugins = model.tags[tag].orEmpty()

val pageTitle = when (plugins.isEmpty()) {
    true -> "No plugins for the tag found."
    false -> "Plugins with tag $tag"
}

pageTemplate(pageTitle) {
    h1 {
        text(pageTitle)
    }

    table {
        thead {
            tr {
                th {
                    text("Title")
                }
            }
        }
        tbody {
            plugins.forEach { plugin ->
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
