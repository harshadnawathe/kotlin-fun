package com.github.harshadnawathe.fp

import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class FpTest {

    @Test
    fun `some test`() {
        val f = { n: Double ->
            sqrt(n)
        }

        runCatching{

        }

        println(f(-4.0))

    }
}