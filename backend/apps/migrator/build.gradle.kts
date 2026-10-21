plugins {
    id("contentria-spring-boot-app-conventions")
}

// Spring 컨텍스트를 띄우지 않는 일반 main이다. 마이그레이션 파일을 가져오려고 각 컨텍스트의 -core를
// runtimeOnly로 두는데, Spring을 띄우면 그 모듈의 JPA 자동 설정까지 따라오기 때문이다.
springBoot {
    mainClass = "com.contentria.app.migrator.MigratorKt"
}

dependencies {
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("ch.qos.logback:logback-classic")

    // 마이그레이션 파일(src/main/resources/db/migration/<컨텍스트>)만 쓴다
    runtimeOnly(project(":contexts:member:member-core"))
    runtimeOnly(project(":contexts:publishing:publishing-core"))
    runtimeOnly(project(":contexts:interaction:interaction-core"))
    runtimeOnly(project(":contexts:notification:notification-core"))
    runtimeOnly(project(":contexts:media:media-core"))
    runtimeOnly(project(":contexts:analytics:analytics-core"))

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)
}
