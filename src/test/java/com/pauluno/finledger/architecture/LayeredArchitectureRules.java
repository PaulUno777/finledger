package com.pauluno.finledger.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class LayeredArchitectureRules {

        @ArchTest
        public static final ArchRule clean_architecture_layers_are_respected = layeredArchitecture()
                        .consideringAllDependencies()
                        .layer("Domain").definedBy("..domain..")
                        .layer("Application").definedBy("..application..")
                        .layer("Presentation").definedBy("..presentation..")
                        .layer("Infrastructure").definedBy("..infrastructure..")

                        // Domain should be independent of everything
                        .whereLayer("Domain").mayNotAccessAnyLayer()
                        // Application should only access the Domain
                        .whereLayer("Application").mayOnlyAccessLayers("Domain")
                        // Presentation should only access the Application
                        .whereLayer("Presentation").mayOnlyAccessLayers("Application")
                        // Infrastructure should only access the Domain and the Application
                        .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application");

        @ArchTest
        public static final ArchRule shared_must_be_completely_independent = noClasses()
                        .that().resideInAPackage("..shared..")
                        .should().dependOnClassesThat().resideInAnyPackage("..domain..")
                        .orShould().dependOnClassesThat().resideInAnyPackage("..application..")
                        .orShould().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                        .orShould().dependOnClassesThat().resideInAnyPackage("..presentation..")
                        .as("The shared package must be a pure utility module with zero dependencies on other layers");
}
