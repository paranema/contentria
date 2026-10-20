package com.contentria.platform.architecture

import com.contentria.alpha.adapter.provided.web.ControllerUsingService
import com.contentria.alpha.adapter.required.beta.BetaAdapter
import com.contentria.alpha.api.AlphaView
import com.contentria.alpha.api.ApiUsingSpring
import com.contentria.alpha.application.required.AlphaRepository
import com.contentria.alpha.application.service.AlphaService
import com.contentria.alpha.application.service.MisplacedRepository
import com.contentria.alpha.application.service.ServiceKnowingAdapter
import com.contentria.alpha.domain.CleanEntity
import com.contentria.alpha.domain.DomainUsingAppPackage
import com.contentria.alpha.domain.DomainUsingOtherContextApi
import com.contentria.alpha.domain.DomainUsingPageable
import com.contentria.alpha.domain.DomainUsingWeb
import com.contentria.app.api.AppEntity
import com.contentria.app.api.AppUsingApiAndConfiguration
import com.contentria.app.api.AppUsingDomain
import com.contentria.gamma.application.GammaService
import com.contentria.gamma.application.ServiceUsingController
import com.contentria.gamma.domain.DomainUsingApplication
import com.contentria.gamma.domain.DomainUsingWebInLayered
import com.contentria.gamma.domain.GammaEntity
import com.contentria.gamma.domain.GammaRepository
import com.contentria.gamma.infra.client.GammaClient
import com.contentria.gamma.presentation.web.ControllerUsingInfra
import com.contentria.gamma.presentation.web.GammaController
import com.contentria.platform.architecture.ContextRules.InternalStructure.HEXAGONAL
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Proves each rule catches what it claims to. Without these, a rule that matches nothing (a
 * misspelled package, an importer that cannot read the class file version) would pass silently.
 */
class ContextRulesTest {

    private val context = "alpha"

    @Test
    @DisplayName("다른 컨텍스트의 -api는 번역 계층 밖에서 참조할 수 없다")
    fun `other context api outside translation layer`() {
        val rule = ContextRules.otherContextApisOnlyFromTranslationLayer(context, HEXAGONAL)

        assertViolation(rule, DomainUsingOtherContextApi::class)
        assertNoViolation(rule, BetaAdapter::class)
        assertNoViolation(rule, DomainUsingAppPackage::class)
    }

    @Test
    @DisplayName("-api 모듈은 Spring과 JPA를 모른다")
    fun `api module is framework free`() {
        val rule = ContextRules.apiModuleIsFrameworkFree(context)

        assertViolation(rule, ApiUsingSpring::class)
        assertNoViolation(rule, AlphaView::class)
    }

    @Test
    @DisplayName("Spring Data는 Repository 마커만 쓸 수 있다")
    fun `spring data only through repository marker`() {
        val rule = ContextRules.springDataOnlyThroughRepositoryMarker(context)

        assertViolation(rule, DomainUsingPageable::class)
        assertNoViolation(rule, AlphaRepository::class)
    }

    @Test
    @DisplayName("헥사고날 domain은 허용된 패키지만 의존한다")
    fun `hexagonal domain depends only on allowed packages`() {
        val rule = ContextRules.hexagonalDomainDependsOnlyOnAllowedPackages(context)

        assertViolation(rule, DomainUsingWeb::class)
        assertNoViolation(rule, CleanEntity::class)
    }

    @Test
    @DisplayName("헥사고날에서 Spring Data 마커는 application/required에서만 쓴다")
    fun `spring data marker only in required ports`() {
        val rule = ContextRules.springDataMarkerOnlyInRequiredPorts(context)

        assertViolation(rule, MisplacedRepository::class)
        assertNoViolation(rule, AlphaRepository::class, AlphaService::class)
    }

    @Test
    @DisplayName("domain과 application은 adapter를 모른다")
    fun `inner layers do not know adapters`() {
        val rule = ContextRules.innerLayersDoNotKnowAdapters(context)

        assertViolation(rule, ServiceKnowingAdapter::class)
        assertNoViolation(rule, AlphaService::class)
    }

    @Test
    @DisplayName("들어오는 어댑터는 서비스 구현체를 모른다")
    fun `provided adapters do not know service implementations`() {
        val rule = ContextRules.providedAdaptersDoNotKnowServiceImplementations(context)

        assertViolation(rule, ControllerUsingService::class)
    }

    @Test
    @DisplayName("레이어드는 presentation, application, infra 순으로만 의존하고 domain은 셋 모두가 쓴다")
    fun `layers depend in one direction`() {
        val rule = ContextRules.layersDependInOneDirection("gamma")

        assertViolation(rule, ServiceUsingController::class, GammaController::class)
        assertViolation(rule, ControllerUsingInfra::class, GammaClient::class)
        assertViolation(rule, DomainUsingApplication::class, GammaService::class)
        assertNoViolation(rule, GammaController::class, GammaService::class, GammaClient::class, GammaEntity::class, GammaRepository::class)
    }

    @Test
    @DisplayName("레이어드 domain은 JPA, Spring Data 마커, 공유 커널만 의존한다")
    fun `layered domain depends only on allowed packages`() {
        val rule = ContextRules.layeredDomainDependsOnlyOnAllowedPackages("gamma")

        assertViolation(rule, DomainUsingWebInLayered::class)
        assertNoViolation(rule, GammaEntity::class, GammaRepository::class)
    }

    @Test
    @DisplayName("app은 컨텍스트의 -api와 설정 클래스만 쓴다")
    fun `app uses only context apis and configurations`() {
        val rule = AppRules.usesOnlyContextApisAndConfigurations()

        assertViolation(rule, AppUsingDomain::class)
        assertNoViolation(rule, AppUsingApiAndConfiguration::class)
    }

    @Test
    @DisplayName("app에는 엔티티가 없다")
    fun `app has no entities`() {
        val rule = AppRules.hasNoEntities()

        assertViolation(rule, AppEntity::class)
        assertNoViolation(rule, AppUsingApiAndConfiguration::class)
    }

    private fun assertViolation(rule: ArchRule, vararg types: KClass<*>) {
        assertThat(rule.evaluate(importClasses(*types)).hasViolation())
            .describedAs("expected a violation of: %s", rule.description)
            .isTrue()
    }

    private fun assertNoViolation(rule: ArchRule, vararg types: KClass<*>) {
        val result = rule.evaluate(importClasses(*types))
        assertThat(result.hasViolation())
            .describedAs("expected no violation, got: %s", result.failureReport.details)
            .isFalse()
    }

    private fun importClasses(vararg types: KClass<*>) =
        ClassFileImporter().importClasses(types.map { it.java })
}
