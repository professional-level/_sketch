package com.example.sketch.utils

object StringExtension {
    fun String.toRequestableDateFormat(): String {
        val rawDate = validateOfRowDate()
        return "00$rawDate"
    }

    fun String.validateOfRowDate(): String {
        val trimRawDate = trim()
        require(trimRawDate.length == 8) { "length of date param must be 8 length" }
        require(trimRawDate.startsWith('2')) { " date must start with 2" }
        return trimRawDate
    }
}