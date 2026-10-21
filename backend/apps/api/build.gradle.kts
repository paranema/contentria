plugins {
    id("contentria-spring-boot-app-conventions")
}

dependencies {
    implementation(project(":contexts:member:member-core"))
    implementation(project(":contexts:publishing:publishing-core"))
    implementation(project(":contexts:interaction:interaction-core"))
    implementation(project(":contexts:notification:notification-core"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    // 컨텍스트 사이의 이벤트: 릴레이와 소비자 (platform/messaging)
    implementation(project(":platform:messaging"))
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)

    // 회원 흐름을 실제 PostgreSQL과 HTTP로 검증한다
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
}
