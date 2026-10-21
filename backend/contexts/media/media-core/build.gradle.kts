plugins {
    id("contentria-spring-library-conventions")
    id("contentria-jpa-conventions")
}

dependencies {
    api(project(":contexts:media:media-api"))
    implementation(project(":shared-kernel"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // adapter/required/storage: S3 API로 R2를 쓴다
    implementation(platform(libs.aws.bom))
    implementation(libs.aws.s3)

    // adapter/provided/batch: 정리 잡. Spring Batch는 잡을 띄우는 apps/batch만 가진다
    compileOnly("org.springframework.boot:spring-boot-starter-batch")

    // adapter/provided/web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.security:spring-security-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)

    // 마이그레이션이 엔티티 매핑과 맞는지 실제 PostgreSQL로 검증한다
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
