package com.github.harshadnawathe.jackson.parse.expressiontree


interface Predicate<in T> {
    fun test(value: T) : Boolean
}