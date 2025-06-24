package com.example.grpcproficiencyclient

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GrpcTestClientApplication

fun main(args: Array<String>) {
    runApplication<GrpcTestClientApplication>(*args)
}
