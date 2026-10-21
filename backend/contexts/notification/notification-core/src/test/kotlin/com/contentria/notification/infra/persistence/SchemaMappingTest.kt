package com.contentria.notification.infra.persistence

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
    @DisplayName("엔티티 매핑이 유효하고 한 이벤트로 한 사람에게 알림 하나가 제약으로 걸려 있다")
    fun `mappings are valid`(@TempDir dir: Path) {
        assertThat(generateSchema(dir.resolve("schema.sql"))).contains(
            "create table notification.notifications",
            "constraint uq_notifications_event_recipient unique (source_event_id, recipient_id)",
        )
    }

    @Test
    @DisplayName("외래키가 없다. 알림이 참조하는 것은 모두 다른 컨텍스트에 있다")
    fun `no foreign keys`(@TempDir dir: Path) {
        assertThat(generateSchema(dir.resolve("schema.sql"))).doesNotContain("foreign key")
    }

    private fun generateSchema(target: Path): String {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
            .applySetting("jakarta.persistence.schema-generation.scripts.create-target", target.toString())
            .build()

        MetadataSources(registry).withNotificationEntities().buildMetadata().buildSessionFactory().close()
        return target.toFile().readText()
    }
}
