import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("contentria-kotlin-common-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.plugin.spring") // kotlin("plugin.spring") 대신 정식 ID 사용
}

// Dockerfile과 CI가 앱마다 같은 경로(build/libs/application.jar)로 실행 JAR를 찾는다.
// 버전이 붙은 이름은 남은 -plain.jar나 이전 버전 JAR와 섞여 잘못 고를 수 있다.
tasks.named<BootJar>("bootJar") {
    archiveFileName = "application.jar"
}

// EnvSampleTest가 읽는 .env.sample은 클래스패스 밖에 있다. 입력으로 두지 않으면 샘플만 바뀌었을 때
// 테스트가 UP-TO-DATE로 건너뛰어진다.
tasks.named<Test>("test") {
    inputs.files(layout.projectDirectory.file(".env.sample"))
        .withPropertyName("envSample")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
