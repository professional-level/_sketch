package com.example.sketch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TempClassTest {
    @Test
    fun test0() {
        TempClass()
    }

    @Test
    fun test1() {
        val value = TempClass.Person("test")
        assert(value.name == "test")
    }

    @Test
    fun test2() {
        assertEquals("김토스", getName(TempClass.Person("김토스")))
        assertEquals("아무개", getName(null))
//        assertEquals("아무개", getName(TempClass.Person(null))) // 추가된 테스트
        // Person 객체의 name을 강제로 null로 설정
//        val person = TempClass.Person("김토스")
//        val kProperty = TempClass.Person::class.declaredMemberProperties.find { it.name == "name" }
//        kProperty?.apply {
//            isAccessible = true
//            (this as kotlin.reflect.KMutableProperty1<TempClass.Person, String?>).set(person, null)
//        }
//
//        assertEquals("아무개", getName(person)) // 추가된 테스트
        // Mockito를 사용하여 Person 객체를 모킹
        val mockPerson = Mockito.mock(TempClass.Person::class.java)
        Mockito.`when`(mockPerson.name).thenReturn(null)

        assertEquals("아무개", getName(mockPerson)) // 추가된 테스트
    }

}
