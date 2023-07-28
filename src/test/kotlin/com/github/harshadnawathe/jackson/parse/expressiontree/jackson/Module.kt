package com.github.harshadnawathe.jackson.parse.expressiontree.jackson

import com.fasterxml.jackson.databind.module.SimpleModule
import com.github.harshadnawathe.jackson.parse.expressiontree.*

fun predicatesJacksonModule() = SimpleModule().apply {
    addDeserializer(Predicate::class, PredicateDeserializer())
    addDeserializer(ReflectionBasedMapperPredicate::class, ReflectionBasedMapperPredicateDeserializer())
    addDeserializer(Equals::class, EqualsDeserializer())
    addDeserializer(NotEquals::class, NotEqualsDeserializer())
    addDeserializer(LessThan::class, LessThanDeserializer())
    addDeserializer(LessThanEquals::class, LessThanEqualsDeserializer())
    addDeserializer(GreaterThan::class, GreaterThanDeserializer())
    addDeserializer(GreaterThanEquals::class, GreaterThanEqualsDeserializer())
    addDeserializer(StartsWith::class, StartsWithDeserializer())
    addDeserializer(EndsWith::class, EndsWithDeserializer())
    addDeserializer(AllOf::class, AllOfDeserializer())
    addDeserializer(AnyOf::class, AnyOfDeserializer())
}