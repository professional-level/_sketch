package com.example.sketch.openapi

import com.example.sketch.configure.Property.Companion.APP_KEY
import com.example.sketch.configure.Property.Companion.APP_SECRET

class HeaderBuilder {
    companion object {
        fun Map<HeaderKey, String>.build(): Map<String, String> {
            return this.mapKeys { it.key.name }
        }

        fun build(token: String, trId: String): Map<HeaderKey, String> {
            return default(token = token, trId = trId)
        } // TODO: 추후 build()에 세부 사항 추가가 없다면, 삭제하고 default() 사용

        fun Map<HeaderKey, String>.addHeader(key: HeaderKey, value: String): Map<HeaderKey, String> {
            return this + mapOf(key to value)
        }

        private fun default(token: String, trId: String): Map<HeaderKey, String> {
            return essential() + mapOf(
                HeaderKey.TR_ID to trId,
                HeaderKey.AUTHORIZATION to "Bearer $token"
            )
        }

        private fun essential(): Map<HeaderKey, String> {
            return mapOf(
                HeaderKey.APP_KEY to APP_KEY,
                HeaderKey.APP_SECRET to APP_SECRET,
            )
        }
    }

    enum class HeaderKey(value: String) {
        AUTHORIZATION("authorization"), // TODO: property에서 관리
        APP_KEY("appkey"),
        APP_SECRET("appsecret"),
        TR_ID("tr_id"),
        CUSTOMER_TYPE("custtype")
        ;

        companion object { // TODO: 삭제
            fun defaultHeaderKeys(): List<HeaderKey> {
                return listOf(
                    AUTHORIZATION,
                    APP_KEY,
                    APP_SECRET,
                    TR_ID
                )
            }
        }
    }
}