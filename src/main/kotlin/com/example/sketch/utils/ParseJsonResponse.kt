package com.example.sketch.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity

object ParseJsonResponse {
    fun parseJsonResponse(response: ResponseEntity<String>): OpenApiResponse = parseJsonString(response.body)

    // tempolary public function
// private
    fun parseJsonString(value: String?): JsonNode {
        val objectMapper = ObjectMapper() // TODO: object mapper를 bean 등록 혹은 static으로 사용
        return objectMapper.readTree(value)
    }

    fun getSpecificField(
        jsonNode: JsonNode,
        fieldName: String,
    ): String? = jsonNode.path(fieldName).asText()
}

typealias OpenApiResponse = JsonNode
