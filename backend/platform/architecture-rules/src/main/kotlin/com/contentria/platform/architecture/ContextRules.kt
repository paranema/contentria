package com.contentria.platform.architecture

import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.properties.HasName.Predicates.name
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * The rules of docs/architecture/02-internal-architecture.md, parameterised by context name.
 *
 * Every rule allows an empty `that()` clause. A context grows layer by layer, and a rule about
 * `adapter.provided` must not fail just because the context has no inbound adapter yet.
 *
 * Dependencies on another context's `-core` are not checked here: the Gradle dependency graph
 * already makes them uncompilable.
 */
object ContextRules {

    private const val SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository"

    /** Where a context of each internal structure may reference another context's `-api`. */
    enum class InternalStructure(internal val translationLayer: String, internal val messageConsumers: String) {
        HEXAGONAL("adapter.required", "adapter.provided.messaging"),
        LAYERED("infra.client", "presentation.messaging"),
    }

    /** Rules every context follows, whatever its internal structure. */
    fun forEveryContext(context: String, structure: InternalStructure): List<ArchRule> = listOf(
        otherContextApisOnlyFromTranslationLayer(context, structure),
        apiModuleIsFrameworkFree(context),
        springDataOnlyThroughRepositoryMarker(context),
    )

    /** Rules for a hexagonal context, including the ones every context follows. */
    fun forHexagonal(context: String): List<ArchRule> =
        forEveryContext(context, InternalStructure.HEXAGONAL) + listOf(
            hexagonalDomainDependsOnlyOnAllowedPackages(context),
            springDataMarkerOnlyInRequiredPorts(context),
            innerLayersDoNotKnowAdapters(context),
            providedAdaptersDoNotKnowServiceImplementations(context),
        )

    fun otherContextApisOnlyFromTranslationLayer(context: String, structure: InternalStructure): ArchRule =
        noClasses()
            .that().resideInAPackage(ContextPackages.of(context, ""))
            .and().resideOutsideOfPackages(
                ContextPackages.of(context, structure.translationLayer),
                ContextPackages.of(context, structure.messageConsumers),
            )
            .should().dependOnClassesThat(ContextPackages.anotherContextsApi(context))
            .because("다른 컨텍스트 호출은 번역 계층 한 곳에서만 한다 (architecture/03 호출 규칙 2)")
            .allowEmptyShould(true)

    fun apiModuleIsFrameworkFree(context: String): ArchRule =
        noClasses()
            .that().resideInAPackage(ContextPackages.of(context, "api"))
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
            .because("-api 모듈은 서비스로 분리하면 그대로 클라이언트 라이브러리가 된다 (architecture/01)")
            .allowEmptyShould(true)

    fun springDataOnlyThroughRepositoryMarker(context: String): ArchRule =
        noClasses()
            .that().resideInAnyPackage(ContextPackages.of(context, "domain"), ContextPackages.of(context, "application"))
            .should().dependOnClassesThat(
                resideInAPackage("org.springframework.data..").and(not(name(SPRING_DATA_REPOSITORY)))
            )
            .because("Spring Data에서는 Repository 마커만 허용한다 (architecture/02 리포지토리)")
            .allowEmptyShould(true)

    fun hexagonalDomainDependsOnlyOnAllowedPackages(context: String): ArchRule =
        classes()
            .that().resideInAPackage(ContextPackages.of(context, "domain"))
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "java..",
                "kotlin..",
                "org.jetbrains.annotations..",
                "jakarta.persistence..",
                "com.contentria.shared..",
                ContextPackages.of(context, "domain"),
            )
            .because("헥사고날의 domain은 JPA 애노테이션과 공유 커널 외에 아무것도 의존하지 않는다 (architecture/02)")
            .allowEmptyShould(true)

    fun springDataMarkerOnlyInRequiredPorts(context: String): ArchRule =
        noClasses()
            .that().resideInAPackage(ContextPackages.of(context, "application"))
            .and().resideOutsideOfPackage(ContextPackages.of(context, "application.required"))
            .should().dependOnClassesThat().resideInAPackage("org.springframework.data..")
            .because("헥사고날에서 Spring Data 마커는 application/required의 리포지토리에서만 쓴다 (architecture/02)")
            .allowEmptyShould(true)

    fun innerLayersDoNotKnowAdapters(context: String): ArchRule =
        noClasses()
            .that().resideInAnyPackage(ContextPackages.of(context, "domain"), ContextPackages.of(context, "application"))
            .should().dependOnClassesThat().resideInAPackage(ContextPackages.of(context, "adapter"))
            .because("의존 방향은 안쪽으로만 향한다 (architecture/02)")
            .allowEmptyShould(true)

    fun providedAdaptersDoNotKnowServiceImplementations(context: String): ArchRule =
        noClasses()
            .that().resideInAPackage(ContextPackages.of(context, "adapter.provided"))
            .should().dependOnClassesThat().resideInAPackage(ContextPackages.of(context, "application.service"))
            .because("진입점은 application/provided나 -api의 포트만 호출한다 (architecture/02)")
            .allowEmptyShould(true)
}
