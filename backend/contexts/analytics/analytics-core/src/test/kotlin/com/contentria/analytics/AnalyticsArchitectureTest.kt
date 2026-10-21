package com.contentria.analytics

import com.contentria.platform.architecture.ContextRules
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.CompositeArchRule

/** analytics is layered (docs/architecture/02-internal-architecture.md, 컨텍스트별 배정). */
@AnalyzeClasses(packages = ["com.contentria.analytics"], importOptions = [ImportOption.DoNotIncludeTests::class])
class AnalyticsArchitectureTest {

    @ArchTest
    val layered: ArchRule = CompositeArchRule.of(ContextRules.forLayered("analytics"))
}
