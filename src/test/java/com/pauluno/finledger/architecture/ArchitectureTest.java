package com.pauluno.finledger.architecture;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(
        packages = "com.finledger",
        importOptions = DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests domainRules =
            ArchTests.in(DomainRules.class);

    @ArchTest
    static final ArchTests applicationRules =
            ArchTests.in(ApplicationRules.class);

    @ArchTest
    static final ArchTests presentationRules =
            ArchTests.in(PresentationRules.class);

    @ArchTest
    static final ArchTests infrastructureRules =
            ArchTests.in(InfrastructureRules.class);

    @ArchTest
    static final ArchTests layeredRules =
            ArchTests.in(LayeredArchitectureRules.class);

}