package io.ktor.web.plugins.pages

import io.ktor.web.plugins.model.*
import kotlinx.html.*

@Deprecated("")
fun buildDevPage(model: AppModel, validationResult: ValidationResult): String = buildPage("Development") {
    if (validationResult.hasErrors) {
        p {
            text("There are errors:")
        }

        p {
            issuesTable(validationResult.collected.filter { it.isError })
        }
    }

    validationResult.collected.filter { !it.isError }.takeIf { it.isNotEmpty() }?.let { warnings ->
        p {
            text("Warnings:")
        }

        p {
            issuesTable(warnings)
        }
    }

    p {
        text("Plugins: ${model.allPlugins.size}.")
    }

    table {
        thead {
            tr {
                th {
                }
                th {
                    text("ID")
                }
                th {
                    text("Title")
                }
                th {
                    text("WebSite")
                }
            }
        }

        tbody {
            model.allPlugins.forEach { plugin ->
                tr {
                    td {
                        val issues = validationResult.collected.filter { it.descriptor == plugin }
                        if (issues.isNotEmpty()) {
                            if (issues.any { it.isError }) {
                                text("errors")
                            } else {
                                text("warnings")
                            }
                        }
                    }
                    td {
                        text(plugin.id)
                    }
                    td {
                        text(plugin.title)
                    }
                    td {
                        a(href = plugin.webSite) {
                            text(plugin.webSite)
                        }
                    }
                }
            }

            if (model.allPlugins.isEmpty()) {
                tr {
                    td {
                        colSpan = "4"
                        text("No plugins found")
                    }
                }
            }
        }
    }
}

private fun P.issuesTable(issues: List<ValidationResult.Issue>) {
    check(issues.isNotEmpty())

    table {
        thead {
            tr {
                th {
                    text("ID")
                }
                th {
                    text("File")
                }
                th {
                    text("Message")
                }
            }
        }

        tbody {
            issues.forEach { issue ->
                tr {
                    td {
                        text(issue.descriptor.id)
                    }
                    td {
                        text(issue.descriptor.filePath ?: "")
                    }
                    td {
                        text(issue.text)
                    }
                }
            }
        }
    }
}