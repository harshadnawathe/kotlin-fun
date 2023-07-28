package com.github.harshadnawathe.jackson.parse.expressiontree

class AllOf<T>(
    private val ps : List<Predicate<T>>
) : Predicate<T> {
    override fun test(value: T): Boolean {
        return ps.all { it.test(value) }
    }
}

class AnyOf<T>(
    private val ps : List<Predicate<T>>
) : Predicate<T> {
    override fun test(value: T): Boolean {
        return ps.any { it.test(value) }
    }
}