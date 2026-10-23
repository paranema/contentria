package com.contentria.platform.architecture

import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText

/**
 * An app's `.env.sample` against the environment variables the app reads (backend/apps/README.md).
 * A sample line names a variable whether or not it is commented out: a commented line documents
 * a default, so the variable still counts as listed.
 */
object EnvSample {

    private val listed = Regex("""^#?\s*([A-Z][A-Z0-9_]*)=""")
    private val placeholder = Regex("""\$\{([A-Z][A-Z0-9_]*)""")

    fun variablesListedIn(sample: Path): Set<String> =
        sample.readLines().mapNotNull { listed.find(it.trim())?.groupValues?.get(1) }.toSet()

    /** Environment-style placeholders only (`${DATABASE_URL:...}`), not property references. */
    fun variablesReadBy(config: Path): Set<String> =
        placeholder.findAll(config.readText()).map { it.groupValues[1] }.toSet()
}
