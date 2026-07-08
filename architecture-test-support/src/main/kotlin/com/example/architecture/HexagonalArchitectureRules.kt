package com.example.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

data class HexagonalArchitectureModule(
    val rootPackage: String,
    val stereotypes: ArchitectureStereotypes = ArchitectureStereotypes.common(),
    val enforceAdapterDomainIsolation: Boolean = true,
    val enforceApplicationAdapterIsolation: Boolean = true,
    val enforceDomainIsolation: Boolean = true,
    val enforceDomainFrameworkIsolation: Boolean = true,
    val enforceBoundaryNaming: Boolean = true,
    val enforceAdapterTechnologyBoundaries: Boolean = true,
    val applicationPortPackages: List<String> = listOf(
        "..application.port.in..",
        "..application.port.out..",
    ),
    val adapterPackages: List<String> = listOf("..adapter.."),
    val domainPackages: List<String> = listOf("..domain.."),
    val applicationPackages: List<String> = listOf("..application.."),
    val controllerPackages: List<String> = listOf("..adapter..in..web.."),
    val persistenceAdapterPackages: List<String> = listOf("..adapter..out..persistence.."),
    val externalApiAdapterPackages: List<String> = listOf("..adapter..out..api.."),
    val schedulerRules: List<SchedulerArchitectureRule> = emptyList(),
)

data class ArchitectureStereotypes(
    val webAdapter: List<String> = emptyList(),
    val persistenceAdapter: List<String> = emptyList(),
    val externalApiAdapter: List<String> = emptyList(),
) {
    companion object {
        fun common(): ArchitectureStereotypes {
            return ArchitectureStereotypes(
                webAdapter = listOf("com.example.common.WebAdapter"),
                persistenceAdapter = listOf("com.example.common.PersistenceAdapter"),
                externalApiAdapter = listOf("com.example.common.ExternalApiAdapter"),
            )
        }
    }
}

data class SchedulerArchitectureRule(
    val schedulerPackages: List<String>,
    val forbiddenAccessPackages: List<String>,
)

object HexagonalArchitectureRules {

    fun verify(module: HexagonalArchitectureModule) {
        val importedClasses = importProductionClasses(module.rootPackage)

        if (module.enforceAdapterDomainIsolation) {
            adaptersShouldNotAccessDomain(importedClasses, module)
        }
        if (module.enforceApplicationAdapterIsolation) {
            applicationShouldNotAccessAdapters(importedClasses, module)
        }
        if (module.enforceDomainIsolation) {
            domainShouldNotAccessApplicationOrAdapters(importedClasses, module)
        }
        if (module.enforceDomainFrameworkIsolation) {
            domainShouldNotDependOnFrameworks(importedClasses, module)
        }
        if (module.enforceBoundaryNaming) {
            portsShouldBeApplicationBoundaryInterfaces(importedClasses, module)
            adaptersShouldNotBeNamedAsPorts(importedClasses, module)
            adapterSuffixedClassesShouldLiveInAdapterPackages(importedClasses, module)
        }
        if (module.enforceAdapterTechnologyBoundaries) {
            persistenceAdaptersShouldNotUseExternalClientTechnologies(importedClasses, module)
            redisAdaptersShouldNotDependOnPersistenceAdapters(importedClasses)
        }

        schedulerRulesShouldCallExpectedLayersOnly(importedClasses, module)
        controllersShouldBeAnnotated(importedClasses, module)
        persistenceAdaptersShouldBeAnnotated(importedClasses, module)
        externalApiAdaptersShouldBeAnnotated(importedClasses, module)
    }

    private fun importProductionClasses(rootPackage: String): JavaClasses {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages(rootPackage)
        require(importedClasses.any()) {
            "No production classes were imported for architecture root package: $rootPackage"
        }
        return importedClasses
    }

    private fun adaptersShouldNotAccessDomain(classes: JavaClasses, module: HexagonalArchitectureModule) {
        noClasses()
            .that().resideInAnyPackage(*module.adapterPackages.toTypedArray())
            .should().accessClassesThat().resideInAnyPackage(*module.domainPackages.toTypedArray())
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun applicationShouldNotAccessAdapters(classes: JavaClasses, module: HexagonalArchitectureModule) {
        noClasses()
            .that().resideInAnyPackage(*module.applicationPackages.toTypedArray())
            .should().accessClassesThat().resideInAnyPackage(*module.adapterPackages.toTypedArray())
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun domainShouldNotAccessApplicationOrAdapters(classes: JavaClasses, module: HexagonalArchitectureModule) {
        noClasses()
            .that().resideInAnyPackage(*module.domainPackages.toTypedArray())
            .should().accessClassesThat().resideInAnyPackage(
                *(module.applicationPackages + module.adapterPackages).toTypedArray(),
            )
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun domainShouldNotDependOnFrameworks(classes: JavaClasses, module: HexagonalArchitectureModule) {
        noClasses()
            .that().resideInAnyPackage(*module.domainPackages.toTypedArray())
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "org.apache.kafka..",
                "jakarta.persistence..",
                "reactor..",
            )
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun portsShouldBeApplicationBoundaryInterfaces(classes: JavaClasses, module: HexagonalArchitectureModule) {
        classes()
            .that().haveSimpleNameEndingWith("Port")
            .should().beInterfaces()
            .andShould().resideInAnyPackage(*module.applicationPortPackages.toTypedArray())
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun adaptersShouldNotBeNamedAsPorts(classes: JavaClasses, module: HexagonalArchitectureModule) {
        noClasses()
            .that().resideInAnyPackage(*module.adapterPackages.toTypedArray())
            .should().haveSimpleNameEndingWith("Port")
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun adapterSuffixedClassesShouldLiveInAdapterPackages(
        classes: JavaClasses,
        module: HexagonalArchitectureModule,
    ) {
        classes()
            .that(adapterImplementationName())
            .should().resideInAnyPackage(*module.adapterPackages.toTypedArray())
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun persistenceAdaptersShouldNotUseExternalClientTechnologies(
        classes: JavaClasses,
        module: HexagonalArchitectureModule,
    ) {
        noClasses()
            .that().resideInAnyPackage(*module.persistenceAdapterPackages.toTypedArray())
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.data.redis..",
                "org.springframework.kafka..",
                "org.springframework.web.reactive.function.client..",
            )
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun redisAdaptersShouldNotDependOnPersistenceAdapters(classes: JavaClasses) {
        noClasses()
            .that().resideInAnyPackage("..adapter..out..redis..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..adapter..out..persistence..",
                "jakarta.persistence..",
            )
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun schedulerRulesShouldCallExpectedLayersOnly(
        classes: JavaClasses,
        module: HexagonalArchitectureModule,
    ) {
        module.schedulerRules.forEach { schedulerRule ->
            noClasses()
                .that().resideInAnyPackage(*schedulerRule.schedulerPackages.toTypedArray())
                .should().accessClassesThat().resideInAnyPackage(
                    *schedulerRule.forbiddenAccessPackages.toTypedArray(),
                )
                .allowEmptyShould(true)
                .check(classes)
        }
    }

    private fun controllersShouldBeAnnotated(classes: JavaClasses, module: HexagonalArchitectureModule) {
        if (module.stereotypes.webAdapter.isEmpty()) return

        classes()
            .that().resideInAnyPackage(*module.controllerPackages.toTypedArray())
            .and().haveSimpleNameEndingWith("Controller")
            .should(beAnnotatedWithAny(module.stereotypes.webAdapter, "web adapter stereotype"))
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun persistenceAdaptersShouldBeAnnotated(classes: JavaClasses, module: HexagonalArchitectureModule) {
        if (module.stereotypes.persistenceAdapter.isEmpty()) return

        classes()
            .that().resideInAnyPackage(*module.persistenceAdapterPackages.toTypedArray())
            .and(adapterImplementationName())
            .should(beAnnotatedWithAny(module.stereotypes.persistenceAdapter, "persistence adapter stereotype"))
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun externalApiAdaptersShouldBeAnnotated(classes: JavaClasses, module: HexagonalArchitectureModule) {
        if (module.stereotypes.externalApiAdapter.isEmpty()) return

        classes()
            .that().resideInAnyPackage(*module.externalApiAdapterPackages.toTypedArray())
            .and(adapterImplementationName())
            .should(beAnnotatedWithAny(module.stereotypes.externalApiAdapter, "external API adapter stereotype"))
            .allowEmptyShould(true)
            .check(classes)
    }

    private fun adapterImplementationName(): DescribedPredicate<JavaClass> {
        return object : DescribedPredicate<JavaClass>("have simple name ending with Adapter and not be annotations") {
            override fun test(input: JavaClass): Boolean {
                return input.simpleName.endsWith("Adapter") && !input.isAnnotation
            }
        }
    }

    private fun beAnnotatedWithAny(annotationNames: List<String>, label: String): ArchCondition<JavaClass> {
        return object : ArchCondition<JavaClass>("be annotated with $label") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val annotated = annotationNames.any { annotationName ->
                    item.isAnnotatedWith(annotationName) || item.isMetaAnnotatedWith(annotationName)
                }
                val message = "${item.name} should be annotated with one of $annotationNames"
                events.add(SimpleConditionEvent(item, annotated, message))
            }
        }
    }
}
