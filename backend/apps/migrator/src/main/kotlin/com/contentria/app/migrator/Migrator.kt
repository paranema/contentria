package com.contentria.app.migrator

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/**
 * A context whose schema this process migrates. The schema name, the migration location and
 * the owning role all follow the context name (docs/architecture/04-persistence-conventions.md).
 */
data class ContextSchema(val context: String) {
    val schema: String get() = context
    val location: String get() = "classpath:db/migration/$context"
}

data class Credentials(val username: String, val password: String)

/**
 * Runs each context's Flyway migrations as that context's own migration role.
 *
 * One Flyway per context, never one for the whole database: each has its own schema, its own
 * history table and its own credentials, so a context's migrations cannot reach another
 * context's schema even by mistake, and a context can later move to its own database by taking
 * its schema with it.
 */
class Migrator(
    private val databaseUrl: String,
    private val credentialsOf: (ContextSchema) -> Credentials,
) {

    fun migrateAll(contexts: List<ContextSchema> = CONTEXTS): List<MigrateResult> = contexts.map(::migrate)

    fun migrate(context: ContextSchema): MigrateResult {
        val credentials = credentialsOf(context)
        return Flyway.configure()
            .dataSource(databaseUrl, credentials.username, credentials.password)
            .schemas(context.schema)
            .defaultSchema(context.schema)
            // The schema and its owner are provisioned beforehand (infra/database/contentria-v2).
            // A migration role that could create schemas could also create one for another context.
            .createSchemas(false)
            .locations(context.location)
            .failOnMissingLocations(true)
            .load()
            .migrate()
    }

    companion object {
        /** Every context with a schema. Add a context here when it gets its first migration. */
        val CONTEXTS = listOf(ContextSchema("member"))
    }
}

/**
 * Entry point of the migration Job (ArgoCD PreSync). Exits non-zero when any migration fails,
 * which stops the deployment before the new application version starts.
 */
fun main() {
    val environment = System.getenv()
    fun required(name: String): String =
        environment[name]?.takeIf { it.isNotBlank() } ?: error("Environment variable $name is not set")

    val migrator = Migrator(required("MIGRATION_DATABASE_URL")) { context ->
        val prefix = context.context.uppercase()
        Credentials(required("${prefix}_MIGRATOR_USERNAME"), required("${prefix}_MIGRATOR_PASSWORD"))
    }

    migrator.migrateAll().forEach { result ->
        println("${result.schemaName}: ${result.migrationsExecuted} migration(s) applied, now at version ${result.targetSchemaVersion ?: result.initialSchemaVersion}")
    }
}
