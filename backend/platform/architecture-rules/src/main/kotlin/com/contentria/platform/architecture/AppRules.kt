package com.contentria.platform.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** Rules for the assembly modules under `apps/` (docs/architecture/01-module-structure.md). */
object AppRules {

    private const val APP_PACKAGE = "com.contentria.app.."

    fun forApps(): List<ArchRule> = listOf(
        usesOnlyContextApisAndConfigurations(),
        hasNoEntities(),
    )

    /**
     * An app depends on its contexts' `-core` modules at build time, so nothing in Gradle stops
     * it from reaching a context's internals. Only the `-api` types and the configuration classes
     * each context publishes are meant to be used.
     */
    fun usesOnlyContextApisAndConfigurations(): ArchRule =
        noClasses()
            .that().resideInAPackage(APP_PACKAGE)
            .should().dependOnClassesThat(ContextPackages.anyContextsInternals())
            .because("app은 컨텍스트의 -api 타입과 공개된 설정 클래스만 쓴다 (architecture/01)")
            .allowEmptyShould(true)

    fun hasNoEntities(): ArchRule =
        noClasses()
            .that().resideInAPackage(APP_PACKAGE)
            .should().beAnnotatedWith("jakarta.persistence.Entity")
            .because("app은 도메인 로직을 담지 않는다 (architecture/01)")
            .allowEmptyShould(true)
}
