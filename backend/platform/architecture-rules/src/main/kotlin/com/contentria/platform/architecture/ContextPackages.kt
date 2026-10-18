package com.contentria.platform.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass

/**
 * Package naming of docs/architecture/01-module-structure.md: a context's code lives under
 * `com.contentria.<context>`, its public contract under `com.contentria.<context>.api`.
 */
internal object ContextPackages {

    private const val ROOT = "com.contentria"

    /** Top-level packages under [ROOT] that are not bounded contexts. */
    private val NOT_CONTEXTS = setOf("app", "shared", "platform")

    /** Internal layers of either structure. Anything outside a context must not reach these. */
    private val INTERNAL_LAYERS = setOf("domain", "application", "adapter", "presentation", "infra")

    /** ArchUnit package pattern for [subPackage] of [context]; an empty [subPackage] means the whole context. */
    fun of(context: String, subPackage: String): String =
        if (subPackage.isEmpty()) "$ROOT.$context.." else "$ROOT.$context.$subPackage.."

    fun anotherContextsApi(context: String): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("belong to another context's -api") {
            override fun test(input: JavaClass): Boolean {
                val (owner, layer) = contextAndLayerOf(input) ?: return false
                return owner != context && layer == "api"
            }
        }

    fun anyContextsInternals(): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("belong to a context's internal layer") {
            override fun test(input: JavaClass): Boolean {
                val (_, layer) = contextAndLayerOf(input) ?: return false
                return layer in INTERNAL_LAYERS
            }
        }

    /** `com.contentria.<context>.<layer>...` → (context, layer), or null outside any context. */
    private fun contextAndLayerOf(javaClass: JavaClass): Pair<String, String>? {
        val parts = javaClass.packageName.split('.')
        if (parts.size < 4 || parts[0] != "com" || parts[1] != "contentria") return null
        if (parts[2] in NOT_CONTEXTS) return null
        return parts[2] to parts[3]
    }
}
