package com.github.harshadnawathe.jackson.parse.expressiontree.jackson

import com.github.harshadnawathe.jackson.parse.expressiontree.AllOf
import com.github.harshadnawathe.jackson.parse.expressiontree.AnyOf
import com.github.harshadnawathe.jackson.parse.expressiontree.Predicate
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

abstract class AbstractReducerDeserializer<T>(
    private val operatorName: String
) : JsonDeserializer<T>() {

    final override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T {
        require(p.currentToken == JsonToken.FIELD_NAME) {
            "unexpected token type ${p.currentToken} at ${p.currentLocation}"
        }

        require(p.currentName == operatorName) {
            "unexpected field name ${p.currentName} at ${p.currentLocation}"
        }

        check(p.nextToken() == JsonToken.START_ARRAY) {
            "unexpected token ${p.currentToken} at ${p.currentLocation}"
        }

        val predicates = mutableListOf<Predicate<*>>()
        while (p.nextToken() != JsonToken.END_ARRAY) {
            if(p.currentToken == JsonToken.START_OBJECT) {
                predicates.add(p.readValueAs(Predicate::class.java))
            }
        }

        return makePredicate(predicates)
    }

    abstract fun makePredicate(predicates: List<Predicate<*>>) : T
}

class AllOfDeserializer : AbstractReducerDeserializer<AllOf<*>>(
    operatorName = "%all"
) {
    override fun makePredicate(predicates: List<Predicate<*>>): AllOf<*> {
        return AllOf(predicates)
    }
}

class AnyOfDeserializer : AbstractReducerDeserializer<AnyOf<*>>(
    operatorName = "%any"
) {
    override fun makePredicate(predicates: List<Predicate<*>>): AnyOf<*> {
        return AnyOf(predicates)
    }
}