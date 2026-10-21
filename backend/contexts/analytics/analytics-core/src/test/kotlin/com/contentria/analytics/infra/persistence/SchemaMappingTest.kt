package com.contentria.analytics.infra.persistence

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Boots Hibernate against the entities with no database: mappings are valid, no foreign keys. */
class SchemaMappingTest {

    @Test
    @DisplayName("엔티티 매핑이 유효하고 외래키가 없다. 블로그와 글은 출판의 것이다")
    fun `mappings are valid`(@TempDir dir: Path) {
        val target = dir.resolve("schema.sql")
        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
            .applySetting("jakarta.persistence.schema-generation.scripts.create-target", target.toString())
            .build()

        MetadataSources(registry).withAnalyticsEntities().buildMetadata().buildSessionFactory().close()

        val script = target.toFile().readText()
        assertThat(script).contains("create table analytics.visits").doesNotContain("foreign key", "_ip", " ip ")
    }
}
