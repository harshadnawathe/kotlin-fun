package com.github.harshadnawathe.jackson

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test


class UserAccount(val accountId: String) {
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    lateinit var recoveryEmail: String
}

class LateInitVarJacksonTest {

    val mapper = jacksonObjectMapper();

    @Test
    fun `should serialize to json`() {

        val account = UserAccount("007")

        println(
            mapper.writeValueAsString(account)
        )

    }
}