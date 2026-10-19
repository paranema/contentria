package com.contentria.member.adapter.required.persistence

import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.Credential
import com.contentria.member.domain.profile.Profile
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Boots Hibernate against the entity classes with no database attached.
 *
 * Building the session factory is what validates the mappings — a `mappedBy` pointing at a
 * field that does not exist, or a collection with no owning side, fails here rather than at
 * application start-up. The generated script is then asserted on so the constraints that
 * back the aggregate's invariants cannot be dropped without a test noticing.
 */
class SchemaMappingTest {

    @Test
    @DisplayName("엔티티 매핑이 유효하고 기대한 스키마를 만든다")
    fun `mappings are valid and produce the expected schema`(@TempDir tempDir: Path) {
        val script = generateSchema(tempDir.resolve("schema.sql"))

        assertThat(script).contains(
            "create table member.accounts",
            "create table member.credentials",
            "create table member.profiles",
            "create table member.account_roles",
        )
    }

    @Test
    @DisplayName("계정당 provider별 자격증명이 하나라는 불변식이 DB 제약으로도 걸려 있다")
    fun `one credential per provider is enforced by a constraint`(@TempDir tempDir: Path) {
        val script = generateSchema(tempDir.resolve("schema.sql"))

        assertThat(script).contains(
            "constraint uq_credentials_account_provider unique (account_id, provider)",
        )
    }

    @Test
    @DisplayName("소셜 identity가 한 계정에만 속한다는 불변식이 DB 제약으로도 걸려 있다")
    fun `one account per social identity is enforced by a constraint`(@TempDir tempDir: Path) {
        val script = generateSchema(tempDir.resolve("schema.sql"))

        assertThat(script).contains(
            "constraint uq_credentials_provider_id unique (provider, provider_id)",
        )
    }

    @Test
    @DisplayName("이메일과 표시명의 유일성이 DB 제약으로 걸려 있다")
    fun `email and display name are unique`(@TempDir tempDir: Path) {
        val script = generateSchema(tempDir.resolve("schema.sql"))

        assertThat(script).contains("email varchar(255) not null unique")
        assertThat(script).contains("display_name varchar(50) not null unique")
    }

    @Test
    @DisplayName("권한 테이블의 기본키가 (계정, 권한)이라 같은 권한이 중복 저장되지 않는다")
    fun `a role cannot be stored twice for one account`(@TempDir tempDir: Path) {
        val script = generateSchema(tempDir.resolve("schema.sql"))

        assertThat(script).contains("primary key (account_id, role)")
    }

    @Test
    @DisplayName("애그리거트 사이에는 연관관계를 매핑하지 않는다. Profile은 Account를 ID로만 참조한다")
    fun `no association crosses an aggregate boundary`(@TempDir tempDir: Path) {
        val script = generateSchema(tempDir.resolve("schema.sql"))

        assertThat(script.lines().filter { it.contains("alter table") })
            .allSatisfy { line -> assertThat(line).doesNotContain("profiles") }
    }

    private fun generateSchema(target: Path): String {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
            .applySetting(
                "jakarta.persistence.schema-generation.scripts.create-target",
                target.toString(),
            )
            .build()

        MetadataSources(registry)
            .addAnnotatedClass(Account::class.java)
            .addAnnotatedClass(Credential::class.java)
            .addAnnotatedClass(Profile::class.java)
            .buildMetadata()
            .buildSessionFactory()
            .close()

        return target.toFile().readText()
    }
}
