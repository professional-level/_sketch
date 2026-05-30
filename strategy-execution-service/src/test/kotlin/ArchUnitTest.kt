import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchUnitTest {

    private val importedClasses: JavaClasses = ClassFileImporter().importPackages(
        "com.example.strategyexecutionservice",
    )

    @Test
    fun `domain should not access application or adapters`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().accessClassesThat().resideInAnyPackage("..application..", "..adapter..")

        rule.check(importedClasses)
    }

    @Test
    fun `domain should not depend on framework classes`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "org.apache.kafka..",
                "jakarta.persistence..",
                "reactor..",
            )

        rule.check(importedClasses)
    }
}
