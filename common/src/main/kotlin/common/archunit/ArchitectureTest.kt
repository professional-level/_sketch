package common.archunit

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.Test

/**
 * 공통 아키텍처 테스트 기본 클래스
 * 모든 서비스에서 상속받아 사용할 수 있는 표준 ArchUnit 테스트
 */
abstract class ArchitectureTest {
    
    /**
     * 각 서비스에서 구체적인 패키지를 지정해야 함
     */
    abstract val basePackage: String
    
    /**
     * 테스트 대상 클래스들
     */
    abstract val javaClasses: JavaClasses

    @Test
    fun `어댑터는 도메인에 직접 접근할 수 없다`() {
        noClasses()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..domain..")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `애플리케이션 레이어는 어댑터 구현에 접근할 수 없다`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `도메인 레이어는 애플리케이션과 어댑터 레이어에 접근할 수 없다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `컨트롤러는 WebAdapter 어노테이션을 가져야 한다`() {
        classes()
            .that().resideInAPackage("..adapter.in.web..")
            .and().areNotInterfaces()
            .should().beAnnotatedWith("common.WebAdapter")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `영속성 어댑터는 PersistenceAdapter 어노테이션을 가져야 한다`() {
        classes()
            .that().resideInAPackage("..adapter.out.persistence..")
            .and().areNotInterfaces()
            .should().beAnnotatedWith("common.PersistenceAdapter")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `외부 API 어댑터는 ExternalApiAdapter 어노테이션을 가져야 한다`() {
        classes()
            .that().resideInAPackage("..adapter.out.api..")
            .and().areNotInterfaces()
            .should().beAnnotatedWith("common.ExternalApiAdapter")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `유스케이스는 UseCase 어노테이션을 가져야 한다`() {
        classes()
            .that().areInterfaces()
            .and().resideInAPackage("..application.port.in..")
            .and().haveSimpleNameEndingWith("UseCase")
            .should().beAnnotatedWith("common.UseCase")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `유스케이스 구현체는 UseCaseImpl 어노테이션을 가져야 한다`() {
        classes()
            .that().resideInAPackage("..application.service..")
            .and().areNotInterfaces()
            .should().beAnnotatedWith("common.UseCaseImpl")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `레이어드 아키텍처 준수 테스트`() {
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Adapter").definedBy("..adapter..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            
            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter")
            
            .check(javaClasses)
    }

    @Test
    fun `도메인 서비스는 도메인 패키지에 있어야 한다`() {
        classes()
            .that().haveSimpleNameEndingWith("DomainService")
            .should().resideInAPackage("..domain..")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `엔티티는 도메인 패키지에 있어야 한다`() {
        classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .and().areNotInnerClasses()
            .should().resideInAPackage("..domain..")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    @Test
    fun `포트 인터페이스는 적절한 패키지에 있어야 한다`() {
        classes()
            .that().areInterfaces()
            .and().haveSimpleNameEndingWith("Port")
            .should().resideInAnyPackage("..application.port.in..", "..application.port.out..")
            .allowEmptyShould(true)
            .check(javaClasses)
    }

    /**
     * 커스텀 아키텍처 룰을 추가할 수 있는 확장 포인트
     */
    open fun customArchitectureRules(): List<() -> Unit> = emptyList()

    @Test
    fun `커스텀 아키텍처 룰 실행`() {
        customArchitectureRules().forEach { rule -> rule.invoke() }
    }
}

/**
 * 특정 패키지의 ArchUnit 테스트를 위한 도우미 함수
 */
fun JavaClass.isInPackage(packageName: String): Boolean {
    return this.packageName.contains(packageName)
}

fun JavaClasses.inPackage(packageName: String): List<JavaClass> {
    return this.filter { it.isInPackage(packageName) }
}