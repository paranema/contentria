plugins {
    id("contentria-spring-library-conventions")
}

// Outbox, relay and inbox shared by every context (docs/architecture/03-context-integration.md, 이벤트).
// Only the outbox writer exists so far; the relay comes with the first consumer.
dependencies {
    implementation(project(":shared-kernel"))

    implementation("org.springframework:spring-jdbc")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
