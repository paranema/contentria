package com.contentria.publishing

import com.contentria.platform.architecture.ContextRules
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.CompositeArchRule

/** publishing is hexagonal (docs/architecture/02-internal-architecture.md, 컨텍스트별 배정). */
@AnalyzeClasses(packages = ["com.contentria.publishing"], importOptions = [ImportOption.DoNotIncludeTests::class])
class PublishingArchitectureTest {

    @ArchTest
    val hexagonal: ArchRule = CompositeArchRule.of(ContextRules.forHexagonal("publishing"))
}
