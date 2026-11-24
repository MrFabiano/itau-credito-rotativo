package br.com.creditorotativo.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fronteiras da arquitetura hexagonal verificadas automaticamente: se alguém importar infraestrutura
 * dentro do domínio, o build quebra. Observação consciente: o domínio usa apenas o estereótipo
 * {@code @Component} do Spring para descoberta automática de regras (ver ADR-002).
 */
@AnalyzeClasses(packages = "br.com.creditorotativo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..", "..repository..", "..controller..", "..messaging..", "..infrastructure..",
                            "org.springframework.controller..", "org.springframework.http..", "org.springframework.data..",
                            "jakarta.persistence..", "io.micrometer..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..", "..messaging..", "..infrastructure..");

    @ArchTest
    static final ArchRule repositoryPortsDependOnlyOnDomain =
            noClasses().that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..", "..controller..", "..messaging..", "..infrastructure..");

    @ArchTest
    static final ArchRule repositoryPackageHoldsOnlyInterfaces =
            classes().that().resideInAPackage("..repository..").should().beInterfaces();
    @ArchTest
    static final ArchRule messagingDoesNotDependOnOtherAdapters =
            noClasses().that().resideInAPackage("..messaging..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..infrastructure..");

    static final ArchRule services_do_not_depend_on_infrastructure_or_controller =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure..", "..controller..", "..messaging..");

    @ArchTest
    static final ArchRule nobodyDependsOnControllers =
            noClasses().that().resideOutsideOfPackage("..controller..")
                    .should().dependOnClassesThat(
                            resideInAPackage("..controller..")
                                    .and(resideOutsideOfPackages("..controller.request..", "..controller.response..")));
    @ArchTest
    static final ArchRule coreDoesNotKnowWebDtos =
            noClasses().that().resideInAnyPackage("..domain..", "..application..", "..service..", "..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controller..");

    @ArchTest
    static final ArchRule servicesDoNotDependOnAdapters =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure..", "..controller..", "..messaging..");
}
