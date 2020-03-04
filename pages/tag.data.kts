produce {
    model.tags.keys.asFlow().map { parametersOf("tag", it) }
}
