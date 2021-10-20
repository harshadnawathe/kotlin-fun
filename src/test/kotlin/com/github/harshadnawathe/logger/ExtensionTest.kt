package com.github.harshadnawathe.logger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class Person {

    fun bar() {

    }

    fun String.foo() {

    }

}


class Email(value: String) {

    init {
        require(isValid(value)) {
            "Invalid email address"
        }
    }

    private fun isValid(value: String) : Boolean {
        value.isNullOrBlank()
        return true
    }
}

fun fooBar(block: Person.() -> Unit) {
    val email1 = Email("foo@bar.com")
    val email2 = Email("foo@bar.com")



}



class ExtensionTest {

    @Test
    fun `should work`() {
        val email1 = Email("foo@bar.com")
        val email2 = Email("foo@bar.com")

        email1.equals(email2)
        println(email1.equals(email2))
    }
}