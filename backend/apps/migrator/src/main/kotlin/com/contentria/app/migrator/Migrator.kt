package com.contentria.app.migrator

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/**
 * 이 프로세스가 스키마를 마이그레이션하는 컨텍스트. 스키마 이름, 마이그레이션 위치, 소유 롤이
 * 모두 컨텍스트 이름을 따른다(docs/architecture/04-persistence-conventions.md).
 */
data class ContextSchema(val context: String) {
    val schema: String get() = context
    val location: String get() = "classpath:db/migration/$context"
    val usernameVariable: String get() = "${context.uppercase()}_MIGRATOR_USERNAME"
    val passwordVariable: String get() = "${context.uppercase()}_MIGRATOR_PASSWORD"
}

/** [main]이 컨텍스트마다의 접속 정보와 함께 읽는다. 전체 목록은 apps/migrator/.env.sample에 있다. */
const val DATABASE_URL_VARIABLE = "MIGRATION_DATABASE_URL"

data class Credentials(val username: String, val password: String)

/**
 * 컨텍스트마다 그 컨텍스트의 마이그레이션 롤로 Flyway 마이그레이션을 실행한다.
 *
 * Flyway는 데이터베이스 전체에 하나가 아니라 컨텍스트마다 하나다. 컨텍스트마다 스키마, 이력 테이블,
 * 접속 정보가 따로라서, 한 컨텍스트의 마이그레이션은 실수로라도 다른 컨텍스트의 스키마에 닿지 못한다.
 * 나중에 한 컨텍스트를 자기 데이터베이스로 옮길 때도 그 스키마만 가져가면 된다.
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
            // 스키마와 그 소유자는 미리 만들어 둔다(infra/database/contentria-v2).
            // 스키마를 만들 수 있는 마이그레이션 롤이라면 다른 컨텍스트의 스키마도 만들 수 있다.
            .createSchemas(false)
            .locations(context.location)
            .failOnMissingLocations(true)
            .load()
            .migrate()
    }

    companion object {
        /** 스키마가 있는 모든 컨텍스트. 컨텍스트에 첫 마이그레이션이 생기면 여기에 더한다. */
        val CONTEXTS = listOf(
            ContextSchema("member"),
            ContextSchema("publishing"),
            ContextSchema("interaction"),
            ContextSchema("notification"),
            ContextSchema("media"),
            ContextSchema("analytics"),
        )
    }
}

/**
 * 마이그레이션 Job(ArgoCD PreSync)의 진입점. 마이그레이션이 하나라도 실패하면 0이 아닌 코드로 끝나고,
 * 그러면 새 버전의 애플리케이션이 뜨기 전에 배포가 멈춘다.
 */
fun main() {
    val environment = System.getenv()
    fun required(name: String): String =
        environment[name]?.takeIf { it.isNotBlank() } ?: error("Environment variable $name is not set")

    val migrator = Migrator(required(DATABASE_URL_VARIABLE)) { context ->
        Credentials(required(context.usernameVariable), required(context.passwordVariable))
    }

    migrator.migrateAll().forEach { result ->
        println("${result.schemaName}: ${result.migrationsExecuted} migration(s) applied, now at version ${result.targetSchemaVersion ?: result.initialSchemaVersion}")
    }
}
