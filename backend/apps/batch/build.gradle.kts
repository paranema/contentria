plugins {
    id("contentria-spring-boot-app-conventions")
}

dependencies {
    // 배치 잡이 생기면 그 잡을 가진 컨텍스트의 -core를 추가한다
    implementation("org.springframework.boot:spring-boot-starter-batch")
}
