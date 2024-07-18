package com.example.stock

import com.example.stock.common.PersistenceAdapter
import com.example.stock.common.WebAdapter
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchUnitTest {

    private val importedClasses: JavaClasses = ClassFileImporter().importPackages("com.example.stock")

    @Test
    fun `adapters should not access domain`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..adapter..")
            .should().accessClassesThat().resideInAnyPackage("..domain..")
        rule.check(importedClasses)
    }

    @Test
    fun `application should not access adapters`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..application..")
            .should().accessClassesThat().resideInAnyPackage("..adapter..")
        rule.check(importedClasses)
    }

    @Test
    fun `domain should not access adapters, application`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().accessClassesThat().resideInAnyPackage("..application..", "..service..")

        rule.check(importedClasses)
    }

    @Test
    fun `controllers should be annotated with WebAdapter`() {
        val rule = classes()
            .that().resideInAPackage("..adapter..in..web..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(WebAdapter::class.java)
        rule.check(importedClasses)
    }

    @Test
    fun `adapters should be annotated with PersistenceAdapter`() {
        val rule = classes()
            .that().resideInAPackage("..adapter..out..persistence..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().beAnnotatedWith(PersistenceAdapter::class.java)
        rule.check(importedClasses)
    }
}
