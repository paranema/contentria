package com.contentria.media.adapter.required.persistence

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Boots Hibernate against the entities with no database: mappings are valid, constraints are declared. */
class SchemaMappingTest {

    @Test
    @DisplayName("엔티티 매핑이 유효하고, 한 파일에 기록 하나와 이미지 안의 연결이 선언되어 있다")
    fun `mappings are valid`(@TempDir dir: Path) {
        val script = generateSchema(dir.resolve("schema.sql"))

        assertThat(script).contains(
            "create table media.media",
            "create table media.media_attachments",
            "constraint uq_media_stored_key unique (stored_key)",
        )
    }

    @Test
    @DisplayName("외래키는 연결에서 이미지로 가는 하나뿐이다. 글은 다른 컨텍스트라 외래키가 없다")
    fun `the only foreign key stays inside the aggregate`(@TempDir dir: Path) {
        val foreignKeys = generateSchema(dir.resolve("schema.sql")).lines().filter { "foreign key" in it }

        assertThat(foreignKeys).singleElement().asString().contains("media.media_attachments", "references media.media")
    }

    private fun generateSchema(target: Path): String {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
            .applySetting("jakarta.persistence.schema-generation.scripts.create-target", target.toString())
            .build()

        MetadataSources(registry).withMediaEntities().buildMetadata().buildSessionFactory().close()
        return target.toFile().readText()
    }
}
