plugins {
    id("contentria-kotlin-library-conventions")
    id("contentria-jpa-conventions")
}

dependencies {
    // AuditableEntity의 매핑 애노테이션만 필요하다. 구현은 각 컨텍스트가 가져온다.
    compileOnly("jakarta.persistence:jakarta.persistence-api")

    testImplementation("org.assertj:assertj-core")
}
