plugins {
    id("contentria-spring-boot-app-conventions")
}

dependencies {
    implementation(project(":contexts:member:member-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    runtimeOnly("org.postgresql:postgresql")
}
