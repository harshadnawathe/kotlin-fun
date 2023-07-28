package com.github.harshadnawathe.jackson.parse.expressiontree

class Equals<T : Any>(
    private val rhs : Comparable<T>
) : Predicate<T> {
    override fun test(value: T) : Boolean {
        return rhs.compareTo(value) == 0
    }
}

class NotEquals<T : Any>(
    private val rhs : Comparable<T>
) : Predicate<T> {
    override fun test(value: T) : Boolean {
        return rhs.compareTo(value) != 0
    }
}

class LessThan<T : Any>(
    private val rhs: Comparable<T>
) : Predicate<T> {
    override fun test(value: T): Boolean {
        return rhs > value
    }
}

class LessThanEquals<T : Any>(
    private val rhs: Comparable<T>
) : Predicate<T> {
    override fun test(value: T): Boolean {
        return rhs >= value
    }
}

class GreaterThan<T : Any>(
    private val rhs: Comparable<T>
) : Predicate<T> {
    override fun test(value: T): Boolean {
        return rhs < value
    }
}

class GreaterThanEquals<T : Any>(
    private val rhs: Comparable<T>
) : Predicate<T> {
    override fun test(value: T): Boolean {
        return rhs <= value
    }
}

class StartsWith(private val prefix: String) : Predicate<String> {
    override fun test(value: String): Boolean {
        return value.startsWith(prefix)
    }
}

class EndsWith(private val suffix: String) : Predicate<String> {
    override fun test(value: String): Boolean {
        return value.endsWith(suffix)
    }
}