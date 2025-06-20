package com.example.common

interface DomainRepository<TEntity, TId> {
    suspend fun findAll(): List<TEntity>
    suspend fun findById(id: TId): TEntity?
    suspend fun save(entity: TEntity): TEntity
    suspend fun saveAll(entities: List<TEntity>): List<TEntity>
    suspend fun existsById(id: TId): Boolean
    suspend fun deleteById(id: TId)
    suspend fun delete(entity: TEntity)
    suspend fun deleteAll()
    suspend fun count(): Long
}
