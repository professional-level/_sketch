package com.example.common

interface DomainRepository<TEntity, TId> {
//    fun findAll(): List<TEntity>
    fun findById(id: TId): TEntity?
//    fun save(entity: TEntity)
}
