package com.pauluno.finledger.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class PresentationRules {

    @ArchTest
    public static final ArchRule presentation_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..presentation..")
                    .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                    .as("Presentation should not depend on the Infrastructure layer");

    @ArchTest
    public static final ArchRule presentation_controllers_should_only_call_application =
            noClasses()
                    .that().resideInAPackage("..presentation..")
                    .should().dependOnClassesThat().resideInAnyPackage("..domain..")
                    .as("Presentation should not depend on the Domain layer");
}
