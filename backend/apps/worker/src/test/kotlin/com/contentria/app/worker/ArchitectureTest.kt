package com.contentria.app.worker

import com.contentria.platform.architecture.AppRules
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.CompositeArchRule

@AnalyzeClasses(packages = ["com.contentria.app.worker"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

    @ArchTest
    val app: ArchRule = CompositeArchRule.of(AppRules.forApps())
}
