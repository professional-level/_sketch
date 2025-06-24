package com.example.grpcproficiency.controller

import com.example.grpcproficiency.entity.TestEntity
import com.example.grpcproficiency.service.TestProtobufService
import com.example.grpcproficiency.service.TestService
import com.google.protobuf.MessageLite
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream

@RestController
class TestController(
    private val testService: TestService,
    private val testProtobufService: TestProtobufService,
) {
    @PostMapping("/dummy/{size}")
    suspend fun setDummys(
        @PathVariable("size") size: Int,
    ) {
        runBlocking {
            testService.setDummy(size)
            testProtobufService.setDummy(size)
        }
    }

    @GetMapping("/proto")
    suspend fun getOneProto(): ByteArray {
        return testProtobufService.getOne()?.toByteArray() ?: ByteArray(0)
    }

    @GetMapping("/json")
    suspend fun getOneJson(): TestEntity {
        return testService.getOne()!!
    }

    @GetMapping("/proto/all")
    suspend fun getAllProto(): ByteArray {
        return testProtobufService.getAll().toByteArray()
    }

    @GetMapping("/json/all")
    suspend fun getAllJson(): Collection<TestEntity> {
        return testService.getAll()
    }
}

/* List 변환을 간단하게 해주는 확장함수 */
fun <T : MessageLite> Collection<T>.toByteArray(): ByteArray {
    val outputStream = ByteArrayOutputStream()
    this.forEach { it.writeDelimitedTo(outputStream) }
    return outputStream.toByteArray()
}
