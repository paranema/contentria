plugins {
    id("contentria-spring-library-conventions")
    id("contentria-jpa-conventions")
}

dependencies {
    api(project(":contexts:member:member-api"))
    implementation(project(":shared-kernel"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.archunit.junit5)
}