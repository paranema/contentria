plugins {
    id("contentria-spring-library-conventions")
    id("contentria-jpa-conventions")
}

dependencies {
    api(project(":contexts:member:member-api"))
    implementation(project(":shared-kernel"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // adapter/provided/web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.security:spring-security-core")

    // adapter/required
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)

    // 마이그레이션이 엔티티 매핑과 맞는지 실제 PostgreSQL로 검증한다
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}