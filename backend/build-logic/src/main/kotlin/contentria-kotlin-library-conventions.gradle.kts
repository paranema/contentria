// Kotlin 라이브러리. Spring Boot 플러그인을 적용하지 않는다.
// <컨텍스트>-api와 shared-kernel이 쓴다. 다른 컨텍스트나 서비스로 분리된 뒤의 클라이언트가
// 가져다 쓸 모듈이므로 Spring 런타임에 의존하지 않는다.
// 의존성 버전만 Spring Boot BOM에서 가져온다.
plugins {
    id("contentria-kotlin-common-conventions")
    id("io.spring.dependency-management")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        val springBootVersion = libs.findVersion("spring-boot").get().requiredVersion
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
}
