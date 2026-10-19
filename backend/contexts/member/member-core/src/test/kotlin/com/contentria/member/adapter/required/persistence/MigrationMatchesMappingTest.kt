package com.contentria.member.adapter.required.persistence

import com.contentria.member.domain.MemberSchema
import com.contentria.member.domain.account.Account
import com.contentria.member.domain.account.Credential
import com.contentria.member.domain.login.LoginAttempt
import com.contentria.member.domain.profile.Profile
import com.contentria.member.domain.session.RefreshToken
import com.contentria.member.domain.signup.SignUpAttempt
import org.assertj.core.api.Assertions.assertThatCode
import org.flywaydb.core.Flyway
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Applies this context's Flyway migrations to a real PostgreSQL and lets Hibernate validate the
 * entity mappings against the result, the same check `ddl-auto: validate` runs at start-up.
 *
 * An entity change without a matching migration fails here instead of at deployment.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationMatchesMappingTest {

    private val postgres = PostgreSQLContainer("postgres:17")

    @BeforeAll
    fun migrate() {
        postgres.start()
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(MemberSchema.NAME)
            .defaultSchema(MemberSchema.NAME)
            .locations("classpath:db/migration/${MemberSchema.NAME}")
            .failOnMissingLocations(true)
            .load()
            .migrate()
    }

    @AfterAll
    fun stop() = postgres.stop()

    @Test
    @DisplayName("마이그레이션으로 만든 스키마가 엔티티 매핑과 일치한다")
    fun `migrated schema matches the entity mappings`() {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("jakarta.persistence.jdbc.url", postgres.jdbcUrl)
            .applySetting("jakarta.persistence.jdbc.user", postgres.username)
            .applySetting("jakarta.persistence.jdbc.password", postgres.password)
            .applySetting("hibernate.hbm2ddl.auto", "validate")
            .build()

        assertThatCode {
            MetadataSources(registry)
                .addAnnotatedClass(Account::class.java)
                .addAnnotatedClass(Credential::class.java)
                .addAnnotatedClass(Profile::class.java)
                .addAnnotatedClass(SignUpAttempt::class.java)
                .addAnnotatedClass(LoginAttempt::class.java)
                .addAnnotatedClass(RefreshToken::class.java)
                .buildMetadata()
                .buildSessionFactory()
                .close()
        }.doesNotThrowAnyException()
    }
}
