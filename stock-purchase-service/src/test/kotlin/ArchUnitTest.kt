package com.example.stockpurchaseservice

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.junit.AnalyzeClasses
import com.example.common.archunit.ArchitectureTest
import org.junit.jupiter.api.TestInstance

/**
 * Stock Purchase Service의 아키텍처 테스트
 * 공통 ArchitectureTest를 상속받아 표준화된 테스트 수행
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AnalyzeClasses(packages = ["com.example.stockpurchaseservice"])
class ArchUnitTest : ArchitectureTest() {
    
    override val basePackage: String = "com.example.stockpurchaseservice"
    
    override val javaClasses: JavaClasses = ClassFileImporter().importPackages(basePackage)
    
    /**
     * Stock Purchase Service 특화 아키텍처 룰 추가
     */
    override fun customArchitectureRules(): List<() -> Unit> {
        return listOf(
            // Stock Purchase Service만의 특별한 룰이 있다면 여기에 추가
        )
    }
}
