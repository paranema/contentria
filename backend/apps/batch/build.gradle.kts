plugins {
    id("contentria-spring-boot-app-conventions")
}

dependencies {
    // 배치 잡을 가진 컨텍스트의 -core
    implementation(project(":contexts:media:media-core"))

    implementation("org.springframework.boot:spring-boot-starter-batch")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)

    // 잡을 실제 PostgreSQL과 S3 API(MinIO)로 검증한다
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-minio")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation(platform(libs.aws.bom))
    testImplementation(libs.aws.s3)
}
