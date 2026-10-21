plugins {
    id("contentria-spring-library-conventions")
    id("contentria-jpa-conventions")
}

dependencies {
    api(project(":contexts:media:media-api"))
    implementation(project(":shared-kernel"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)

    // 마이그레이션이 엔티티 매핑과 맞는지 실제 PostgreSQL로 검증한다
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
