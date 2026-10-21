plugins {
    id("contentria-spring-library-conventions")
    id("contentria-jpa-conventions")
}

dependencies {
    api(project(":contexts:publishing:publishing-api"))
    implementation(project(":shared-kernel"))

    // adapter/required/member, adapter/required/media만 쓴다 (ArchUnit이 강제)
    implementation(project(":contexts:member:member-api"))
    implementation(project(":contexts:media:media-api"))

    // adapter/required/messaging: 아웃박스 기록
    implementation(project(":platform:messaging"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // adapter/provided/web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.security:spring-security-core")

    // adapter/required/markdown: 본문에서 요약을 뽑는다
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)

    // 마이그레이션이 엔티티 매핑과 맞는지 실제 PostgreSQL로 검증한다
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
