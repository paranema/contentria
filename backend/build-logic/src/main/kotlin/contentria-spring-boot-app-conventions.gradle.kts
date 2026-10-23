plugins {
    id("contentria-kotlin-common-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.plugin.spring") // kotlin("plugin.spring") 대신 정식 ID 사용
}

// EnvSampleTest가 읽는 .env.sample은 클래스패스 밖에 있다. 입력으로 두지 않으면 샘플만 바뀌었을 때
// 테스트가 UP-TO-DATE로 건너뛰어진다.
tasks.named<Test>("test") {
    inputs.files(layout.projectDirectory.file(".env.sample"))
        .withPropertyName("envSample")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}