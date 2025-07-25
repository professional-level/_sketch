package com.example.grpcproficiency.service

import com.example.grpcproficiency.entity.TestEntity
import com.example.grpcproficiency.repository.TestProtobufRepository
import com.example.grpcproficiency.repository.TestRepository
import example.Protobuff
import org.springframework.stereotype.Service

@Service
class TestService(
    private val repository: TestRepository,
) {
    suspend fun getOne(): TestEntity? {
        return repository.findOne()
    }

    suspend fun getAll(): Collection<TestEntity> {
        return repository.findAll().values
    }

    suspend fun setDummy(size: Int) {
        repository.setDummy(size)
    }
}

@Service
class TestProtobufService(
    private val repository: TestProtobufRepository,
) {
    suspend fun getOne(): Protobuff.TestEntity? {
        return repository.findOne()
    }

    suspend fun getAll(): Collection<Protobuff.TestEntity> {
        return repository.findAll().values
    }

    suspend fun setDummy(size: Int) {
        repository.setDummy(size)
    }
}
