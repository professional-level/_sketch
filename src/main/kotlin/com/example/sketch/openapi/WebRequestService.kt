package com.example.sketch.openapi

import com.example.sketch.configure.Property.Companion.APP_KEY
import com.example.sketch.configure.Property.Companion.APP_SECRET
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.util.Base64

class HeaderBuilder {
    companion object {
        fun Map<HeaderKey, String>.build(): Map<String, String> {
            return this.mapKeys { it.key.value }
        }

        fun build(token: String, trId: String): Map<HeaderKey, String> {
            return default(token = token, trId = trId)
        } // TODO: 추후 build()에 세부 사항 추가가 없다면, 삭제하고 default() 사용

        fun Map<HeaderKey, String>.addHeader(key: HeaderKey, value: String): Map<HeaderKey, String> {
            return this + mapOf(key to value)
        }

        fun Map<HeaderKey, String>.addHashKey(body: Any): Map<HeaderKey, String> {
            val hashKey = generateHashKey(body)
            return this + mapOf(HeaderKey.HASH_KEY to hashKey)
        }

        private fun generateHashKey(body: Any): String {
            // HASH 암호화 로직 구현
            val mapper = jacksonObjectMapper()
            val jsonString = mapper.writeValueAsString(body)
            // SHA-512 또는 다른 알고리즘으로 해싱
            val digest = MessageDigest.getInstance("SHA-512")
            val hashBytes = digest.digest(jsonString.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(hashBytes)
        }

        private fun default(token: String, trId: String): Map<HeaderKey, String> {
            return essential() + mapOf(
                HeaderKey.TR_ID to trId,
                HeaderKey.AUTHORIZATION to "Bearer $token",
            )
        }

        private fun essential(): Map<HeaderKey, String> {
            return mapOf(
                HeaderKey.APP_KEY to APP_KEY,
                HeaderKey.APP_SECRET to APP_SECRET,
            )
        }
    }

    enum class HeaderKey(val value: String) {
        AUTHORIZATION("authorization"), // TODO: property에서 관리
        APP_KEY("appkey"),
        APP_SECRET("appsecret"),
        TR_ID("tr_id"),
        CUSTOMER_TYPE("custtype"),
        HASH_KEY("hashkey"), // HASH_KEY 추가
        ;

        companion object { // TODO: 삭제
            fun defaultHeaderKeys(): List<HeaderKey> {
                return listOf(
                    AUTHORIZATION,
                    APP_KEY,
                    APP_SECRET,
                    TR_ID,
                )
            }
        }
    }
}
