package com.pauluno.finledger.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class DomainRules {

    @ArchTest
    public static final ArchRule domain_should_be_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("Domain should not depend on any Spring component");

    @ArchTest
    public static final ArchRule domain_should_not_depend_on_persistence = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.persistence..")
            .orShould().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..")
            .orShould().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.data..")
            .as("Domain should not depend on any persistence annotations or classes (JPA/Hibernate)");

    @ArchTest
    public static final ArchRule domain_should_not_depend_on_other_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..presentation..")
            .orShould().dependOnClassesThat().resideInAnyPackage("..application..")
            .orShould().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
            .as("Domain should not depend on any other layer");

    @ArchTest
    public static final ArchRule security_policy_should_be_framework_free = noClasses()
            .that().resideInAPackage("..security.policy..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("finledger-security-policy must stay pure JDK (no Spring)");
}
