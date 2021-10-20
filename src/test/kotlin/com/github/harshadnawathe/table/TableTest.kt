package com.github.harshadnawathe.table

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

class UsualTableTest {


    @TestFactory
    fun someTableTest(): Stream<DynamicTest> {
        class TestCase(
            val name: String,
            val a: Int,
            val b: Int,
            val c: Int
        ) {
            fun check() {
                assertThat(a + b).isEqualTo(c)
            }
        }

        val tests = listOf(
            TestCase("First argument 0", 0, 2, 2),
            TestCase("Second argument 0", 2, 0, 2),
            TestCase("Both arguments non zero", 2, 3, 5),
        )

        return DynamicTest.stream(tests.stream(), TestCase::name, TestCase::check)
    }
}

interface TestCase {
    val name: String
}

abstract class TableTest<T : TestCase>(
    private val tests: List<T>,
    private val check: T.() -> Unit
) {
    @TestFactory
    internal fun runner(): Stream<DynamicTest> = DynamicTest.stream(tests.stream(), { it.name }, { it.check() })
}

data class SumTestCase(
    override val name: String,
    val arg1: Int,
    val arg2: Int,
    val result: Int
) : TestCase

class SumTest : TableTest<SumTestCase>(
    listOf(
        SumTestCase("First argument 0", 0, 2, 2)
    ),
    { assertThat(arg1 + arg2).isEqualTo(result) })




