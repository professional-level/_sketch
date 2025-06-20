package com.example.common.infrastructure.repository

import com.example.common.DomainRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.Repository

/**
 * 통합된 Reactive Repository 지원 인터페이스
 * 모든 서비스의 repository가 구현해야 하는 기본 reactive 인터페이스
 */
@NoRepositoryBean
interface ReactiveRepositorySupport<T, ID> : Repository<T, ID> {
    
    /**
     * 엔티티 저장
     */
    suspend fun save(entity: T): T
    
    /**
     * 여러 엔티티 저장
     */
    suspend fun saveAll(entities: List<T>): List<T>
    
    /**
     * ID로 조회
     */
    suspend fun findById(id: ID): T?
    
    /**
     * 모든 엔티티 조회
     */
    suspend fun findAll(): List<T>
    
    /**
     * ID로 존재 여부 확인
     */
    suspend fun existsById(id: ID): Boolean
    
    /**
     * ID로 삭제
     */
    suspend fun deleteById(id: ID)
    
    /**
     * 엔티티 삭제
     */
    suspend fun delete(entity: T)
    
    /**
     * 모든 엔티티 삭제
     */
    suspend fun deleteAll()
    
    /**
     * 전체 개수 조회
     */
    suspend fun count(): Long
}

/**
 * 도메인 레포지토리와 인프라 레포지토리를 연결하는 어댑터 기본 클래스
 */
abstract class ReactiveRepositoryAdapter<T, ID, DOMAIN_ENTITY, DOMAIN_ID>(
    protected val reactiveRepository: ReactiveRepositorySupport<T, ID>
) : DomainRepository<DOMAIN_ENTITY, DOMAIN_ID> {
    
    /**
     * 도메인 엔티티를 인프라 엔티티로 변환
     */
    protected abstract fun toInfrastructureEntity(domainEntity: DOMAIN_ENTITY): T
    
    /**
     * 인프라 엔티티를 도메인 엔티티로 변환
     */
    protected abstract fun toDomainEntity(infrastructureEntity: T): DOMAIN_ENTITY
    
    /**
     * 도메인 ID를 인프라 ID로 변환
     */
    protected abstract fun toInfrastructureId(domainId: DOMAIN_ID): ID
    
    /**
     * 인프라 ID를 도메인 ID로 변환
     */
    protected abstract fun toDomainId(infrastructureId: ID): DOMAIN_ID
    
    override suspend fun save(entity: DOMAIN_ENTITY): DOMAIN_ENTITY {
        val infrastructureEntity = toInfrastructureEntity(entity)
        val savedEntity = reactiveRepository.save(infrastructureEntity)
        return toDomainEntity(savedEntity)
    }
    
    override suspend fun saveAll(entities: List<DOMAIN_ENTITY>): List<DOMAIN_ENTITY> {
        val infrastructureEntities = entities.map { toInfrastructureEntity(it) }
        val savedEntities = reactiveRepository.saveAll(infrastructureEntities)
        return savedEntities.map { toDomainEntity(it) }
    }
    
    override suspend fun findById(id: DOMAIN_ID): DOMAIN_ENTITY? {
        val infrastructureId = toInfrastructureId(id)
        val infrastructureEntity = reactiveRepository.findById(infrastructureId)
        return infrastructureEntity?.let { toDomainEntity(it) }
    }
    
    override suspend fun findAll(): List<DOMAIN_ENTITY> {
        val infrastructureEntities = reactiveRepository.findAll()
        return infrastructureEntities.map { toDomainEntity(it) }
    }
    
    override suspend fun existsById(id: DOMAIN_ID): Boolean {
        val infrastructureId = toInfrastructureId(id)
        return reactiveRepository.existsById(infrastructureId)
    }
    
    override suspend fun deleteById(id: DOMAIN_ID) {
        val infrastructureId = toInfrastructureId(id)
        reactiveRepository.deleteById(infrastructureId)
    }
    
    override suspend fun delete(entity: DOMAIN_ENTITY) {
        val infrastructureEntity = toInfrastructureEntity(entity)
        reactiveRepository.delete(infrastructureEntity)
    }
    
    override suspend fun deleteAll() {
        reactiveRepository.deleteAll()
    }
    
    override suspend fun count(): Long {
        return reactiveRepository.count()
    }
}