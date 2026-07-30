package com.pauluno.finledger.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ApplicationRules {
    @ArchTest
    public static final ArchRule application_should_not_depend_on_presentation = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..presentation..")
            .allowEmptyShould(true)
            .as("Application should not depend on the Presentation layer");

    @ArchTest
    public static final ArchRule application_should_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
            .allowEmptyShould(true)
            .as("Application should not depend on the Infrastructure layer");
}
