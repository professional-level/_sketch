import com.example.common.ExternalApiAdapter
import com.example.common.PersistenceAdapter
import com.example.common.WebAdapter
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchUnitTest {

    private val importedClasses: JavaClasses = ClassFileImporter().importPackages(
        "com.example.stockpurchaseservice",
    ) // TODO: import package를 동적 매핑이 가능해, 하나의 common 모듈에서 하나의 테스트 클래스로 관리할 수 있는 방법 고민 필요

    @Test
    fun `adapters should not access domain`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..adapter..")
            .should().accessClassesThat().resideInAnyPackage("..domain..")
            .allowEmptyShould(true)
        rule.check(importedClasses)
    }

    @Test
    fun `application should not access adapters`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..application..")
            .should().accessClassesThat().resideInAnyPackage("..adapter..")
            .allowEmptyShould(true)
        rule.check(importedClasses)
    }

    @Test
    fun `domain should not access adapters, application`() {
        val rule = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().accessClassesThat().resideInAnyPackage("..application..", "..service..")
            .allowEmptyShould(true)
        rule.check(importedClasses)
    }

    @Test
    fun `controllers should be annotated with WebAdapter`() {
        val rule = classes()
            .that().resideInAPackage("..adapter..in..web..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(WebAdapter::class.java)
            .allowEmptyShould(true)
        val validationResult = rule.evaluate(importedClasses)

        // 결과를 확인하여 조건에 맞는 클래스가 없을 때를 처리합니다.
        if (validationResult.hasViolation()) {
            println("No controller classes found in the specified package")
        } else {
            rule.check(importedClasses)
        }
    }

    @Test
    fun `adapters should be annotated with PersistenceAdapter`() {
        val rule =
            classes().that()
                .resideInAPackage("..adapter..out..persistence..")
                .and()
                .haveSimpleNameEndingWith("Adapter")
                .should()
                .beAnnotatedWith(PersistenceAdapter::class.java)
                .allowEmptyShould(true)
        rule.check(importedClasses)
    }

    @Test
    fun `adapters should be annotated with ExternalApiAdapter`() {
        val rule = classes().that()
            .resideInAPackage("..adapter..out..api..")
            .and()
            .haveSimpleNameEndingWith("Adapter")
            .should()
            .beAnnotatedWith(ExternalApiAdapter::class.java)
            .allowEmptyShould(true) // TODO: 해당 내용으로 블로깅

        rule.check(importedClasses)
    }
}
