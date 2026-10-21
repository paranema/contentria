plugins {
    id("contentria-spring-library-conventions")
    id("contentria-jpa-conventions")
}

dependencies {
    api(project(":contexts:notification:notification-api"))
    implementation(project(":shared-kernel"))

    // infra/client와 presentation/messaging만 쓴다 (ArchUnit이 강제). 출판은 이벤트만 받는다
    implementation(project(":contexts:interaction:interaction-api"))
    implementation(project(":contexts:publishing:publishing-api"))

    // presentation/messaging: 소비자와 인박스
    implementation(project(":platform:messaging"))

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
