package com.github.harshadnawathe.jackson.parse.expressiontree

import kotlin.reflect.full.memberProperties

class MapperPredicate<out TargetT : Any, in SourceT: Any>(
    private val mapper : SourceT.() -> TargetT,
    private val p : Predicate<TargetT>
)  : Predicate<SourceT> {
    override fun test(value: SourceT): Boolean {
        return p.test(mapper(value))
    }
}

@Suppress("UNCHECKED_CAST")
class ReflectionBasedMapperPredicate<out TargetT : Any>(
    private val propertyName : String,
    private val p : Predicate<TargetT>
)  : Predicate<Any> {
    override fun test(value: Any): Boolean {
        val property = value::class.memberProperties.firstOrNull {
            it.name == propertyName
        }
        checkNotNull(property) {
            "no such property named $propertyName"
        }

        return p.test(property.getter.call(value) as TargetT)
    }
}