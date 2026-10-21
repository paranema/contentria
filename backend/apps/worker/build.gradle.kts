plugins {
    id("contentria-spring-boot-app-conventions")
}

dependencies {
    // 영상 변환을 가진 컨텍스트의 -core
    implementation(project(":contexts:media:media-core"))

    implementation("org.springframework.boot:spring-boot-starter")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)

    // 변환을 실제 PostgreSQL, S3 API(MinIO), ffmpeg 컨테이너로 검증한다
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-minio")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation(platform(libs.aws.bom))
    testImplementation(libs.aws.s3)
}
