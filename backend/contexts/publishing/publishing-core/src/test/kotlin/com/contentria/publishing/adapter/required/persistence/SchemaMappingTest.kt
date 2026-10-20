package com.contentria.publishing.adapter.required.persistence

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Boots Hibernate against the entity classes with no database attached. Building the session
 * factory validates the mappings (a `mappedBy` naming a missing field fails here), and the
 * generated script is asserted on so the constraints behind the rules cannot be dropped silently.
 */
class SchemaMappingTest {

    @Test
    @DisplayName("엔티티 매핑이 유효하고 기대한 테이블을 만든다")
    fun `mappings are valid`(@TempDir dir: Path) {
        assertThat(generateSchema(dir.resolve("schema.sql"))).contains(
            "create table publishing.blogs",
            "create table publishing.category_trees",
            "create table publishing.categories",
            "create table publishing.posts",
            "create table publishing.post_slugs",
        )
    }

    @Test
    @DisplayName("블로그 slug, 카테고리 slug, 글의 현재와 옛 slug의 고유성이 DB 제약으로 걸려 있다")
    fun `uniqueness rules have constraints`(@TempDir dir: Path) {
        val script = generateSchema(dir.resolve("schema.sql"))

        assertThat(script).contains("slug varchar(30) not null unique")
        assertThat(script).contains("constraint uq_categories_tree_slug unique (tree_id, slug)")
        assertThat(script).contains("constraint uq_post_slugs_blog_slug unique (blog_id, slug)")
    }

    @Test
    @DisplayName("외래키는 애그리거트 안에만 있다. 글은 블로그와 카테고리를 ID로만 참조한다")
    fun `no association crosses an aggregate boundary`(@TempDir dir: Path) {
        val foreignKeys = generateSchema(dir.resolve("schema.sql")).lines()
            .filter { it.contains("foreign key") }
            .map { it.substringAfter("alter table if exists ").substringBefore(" ") to it.substringAfter("references ").trim().removeSuffix(";") }

        assertThat(foreignKeys).containsExactlyInAnyOrder(
            "publishing.categories" to "publishing.category_trees",
            "publishing.post_slugs" to "publishing.posts",
        )
    }

    private fun generateSchema(target: Path): String {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
            .applySetting("jakarta.persistence.schema-generation.scripts.create-target", target.toString())
            .build()

        MetadataSources(registry).withPublishingEntities().buildMetadata().buildSessionFactory().close()
        return target.toFile().readText()
    }
}
