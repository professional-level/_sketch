package com.example.sketch

import com.example.sketch.TempClass.Person

class TempClass {

    data class Person(val name: String)
}
fun getName(person: Person?) = person?.name ?: "아무개"