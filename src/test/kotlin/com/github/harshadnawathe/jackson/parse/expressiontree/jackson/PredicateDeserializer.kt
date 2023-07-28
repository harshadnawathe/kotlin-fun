package com.github.harshadnawathe.jackson.parse.expressiontree.jackson


import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.github.harshadnawathe.jackson.parse.expressiontree.*

class PredicateDeserializer : JsonDeserializer<Predicate<*>>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Predicate<*> {
        require(p.currentToken() == JsonToken.START_OBJECT) {
            "unexpected token: ${p.currentToken} at ${p.currentLocation}"
        }

        val key = p.nextFieldName()

        if(key in REDUCERS) {
            return p.readValueAs(REDUCERS[key]!!.java)
        }

        if(p.parsingContext.nestingDepth > 1 && key in OPERATORS) {
            return p.readValueAs(OPERATORS[key]!!.java)
        }

        check(!isKeyword(key)) {
            "unexpected keyword $key at ${p.currentLocation}"
        }

        return p.readValueAs(ReflectionBasedMapperPredicate::class.java)
    }

    companion object {
        private fun isKeyword(key: String) = key[0] == '%'
        private val REDUCERS = mapOf(
            "%all" to AllOf::class,
            "%any" to AnyOf::class,
        )
        private val OPERATORS = mapOf(
            "%eq" to Equals::class,
            "%neq" to NotEquals::class,
            "%lt" to LessThan::class,
            "%lt" to LessThan::class,
            "%lte" to LessThanEquals::class,
            "%gt" to GreaterThan::class,
            "%gte" to GreaterThanEquals::class,
            "%startsWith" to StartsWith::class,
            "%endsWith" to EndsWith::class,
        )
    }
}