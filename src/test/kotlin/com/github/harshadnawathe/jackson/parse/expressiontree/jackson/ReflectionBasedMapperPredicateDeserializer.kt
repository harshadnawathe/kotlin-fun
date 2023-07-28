package com.github.harshadnawathe.jackson.parse.expressiontree.jackson

import com.github.harshadnawathe.jackson.parse.expressiontree.Predicate
import com.github.harshadnawathe.jackson.parse.expressiontree.ReflectionBasedMapperPredicate
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

class ReflectionBasedMapperPredicateDeserializer : JsonDeserializer<ReflectionBasedMapperPredicate<*>>(){

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ReflectionBasedMapperPredicate<*> {
        require(p.currentToken() == JsonToken.FIELD_NAME)
        val propertyName = p.currentName

        check(p.nextToken() == JsonToken.START_OBJECT) {
            "unexpected token: ${p.currentToken} at ${p.currentLocation}"
        }

        return ReflectionBasedMapperPredicate(
            propertyName,
            p.readValueAs(Predicate::class.java)
        )
    }

}