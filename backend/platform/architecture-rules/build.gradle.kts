// 테스트 전용 라이브러리. 각 컨텍스트와 app의 아키텍처 테스트가 testImplementation으로 쓴다.
plugins {
    id("contentria-kotlin-library-conventions")
}

dependencies {
    api(libs.archunit)

    testImplementation("org.assertj:assertj-core")

    // 규칙 자체를 검증하는 픽스처가 참조하는 타입
    testImplementation("jakarta.persistence:jakarta.persistence-api")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework.data:spring-data-commons")
}
