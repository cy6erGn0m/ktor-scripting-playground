package io.ktor.web.plugins.scripting

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
@Retention
annotation class PageRoute(val routePath: String)

@Target(AnnotationTarget.FILE)
@Retention
annotation class PageLocation(val locationClassFqName: String)
