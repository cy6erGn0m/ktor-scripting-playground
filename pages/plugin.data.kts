produce {
    model.allPlugins.asFlow().map { parametersOf("pluginId", it.id) }
}