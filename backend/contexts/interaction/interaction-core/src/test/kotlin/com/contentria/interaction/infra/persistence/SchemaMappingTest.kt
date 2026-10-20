package com.contentria.interaction.infra.persistence

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
    @DisplayName("엔티티 매핑이 유효하고 한 번만 구독, 한 번만 좋아요가 제약으로 걸려 있다")
    fun `mappings are valid`(@TempDir dir: Path) {
        val script = generateSchema(dir.resolve("schema.sql"))

        assertThat(script).contains(
            "create table interaction.subscriptions",
            "create table interaction.likes",
            "create table interaction.comments",
            "constraint uq_subscriptions_subscriber_blog unique (subscriber_id, blog_id)",
            "constraint uq_likes_account_post unique (account_id, post_id)",
        )
    }

    @Test
    @DisplayName("외래키가 없다. 답글의 부모도 다른 애그리거트다")
    fun `no foreign keys`(@TempDir dir: Path) {
        assertThat(generateSchema(dir.resolve("schema.sql"))).doesNotContain("foreign key")
    }

    private fun generateSchema(target: Path): String {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
            .applySetting("jakarta.persistence.schema-generation.scripts.create-target", target.toString())
            .build()

        MetadataSources(registry).withInteractionEntities().buildMetadata().buildSessionFactory().close()
        return target.toFile().readText()
    }
}
