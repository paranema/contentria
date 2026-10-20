plugins {
    id("contentria-spring-library-conventions")
}

// Outbox, relay and inbox shared by every context (docs/architecture/03-context-integration.md, 이벤트).
dependencies {
    implementation(project(":shared-kernel"))

    implementation("org.springframework:spring-jdbc")
    api("tools.jackson.module:jackson-module-kotlin")
    // The relay sends with KafkaTemplate; the listener's retries and dead letters are configured here
    api("org.springframework.kafka:spring-kafka")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
