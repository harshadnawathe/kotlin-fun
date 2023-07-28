package com.github.harshadnawathe.jackson.parse.expressiontree.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.github.harshadnawathe.jackson.parse.expressiontree.*

abstract class AbstractPredicatesDeserializer<T>(
    private val operatorName: String,
    private val supportedTypes: List<JsonToken>
) : JsonDeserializer<T>() {

    final override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T {
        require(p.currentToken == JsonToken.FIELD_NAME) {
            "unexpected token type ${p.currentToken} at ${p.currentLocation}"
        }

        require(p.currentName == operatorName) {
            "unexpected field name ${p.currentName} at ${p.currentLocation}"
        }

        check(p.nextValue() in supportedTypes) {
            "unexpected token ${p.currentToken} at ${p.currentLocation}"
        }

        return makePredicate(p.readValueAs(Any::class.java))
    }

    abstract fun makePredicate(value: Any) : T
}

class EqualsDeserializer : AbstractPredicatesDeserializer<Equals<*>>(
    operatorName = "%eq",
    supportedTypes = SUPPORTED_TYPES
) {
    override fun makePredicate(value: Any): Equals<*> {
       return Equals(value as Comparable<*>)
    }

    companion object {
        private val SUPPORTED_TYPES = listOf(
            JsonToken.VALUE_STRING,
            JsonToken.VALUE_TRUE,
            JsonToken.VALUE_FALSE,
            JsonToken.VALUE_NUMBER_FLOAT,
            JsonToken.VALUE_NUMBER_INT,
        )
    }
}

class NotEqualsDeserializer : AbstractPredicatesDeserializer<NotEquals<*>>(
    operatorName = "%neq",
    supportedTypes = SUPPORTED_TYPES
) {
    override fun makePredicate(value: Any): NotEquals<*> {
        return NotEquals(value as Comparable<*>)
    }

    companion object {
        private val SUPPORTED_TYPES = listOf(
            JsonToken.VALUE_STRING,
            JsonToken.VALUE_TRUE,
            JsonToken.VALUE_FALSE,
            JsonToken.VALUE_NUMBER_FLOAT,
            JsonToken.VALUE_NUMBER_INT,
        )
    }
}

class LessThanDeserializer : AbstractPredicatesDeserializer<LessThan<*>>(
    operatorName = "%lt",
    supportedTypes = SUPPORTED_TYPES
) {
    override fun makePredicate(value: Any): LessThan<*> {
        return LessThan(value as Comparable<*>)
    }

    companion object {
        private val SUPPORTED_TYPES = listOf(
            JsonToken.VALUE_STRING,
            JsonToken.VALUE_NUMBER_FLOAT,
            JsonToken.VALUE_NUMBER_INT,
        )
    }
}

class LessThanEqualsDeserializer : AbstractPredicatesDeserializer<LessThanEquals<*>>(
    operatorName = "%lte",
    supportedTypes = SUPPORTED_TYPES
) {
    override fun makePredicate(value: Any): LessThanEquals<*> {
        return LessThanEquals(value as Comparable<*>)
    }

    companion object {
        private val SUPPORTED_TYPES = listOf(
            JsonToken.VALUE_STRING,
            JsonToken.VALUE_NUMBER_FLOAT,
            JsonToken.VALUE_NUMBER_INT,
        )
    }
}

class GreaterThanDeserializer : AbstractPredicatesDeserializer<GreaterThan<*>>(
    operatorName = "%gt",
    supportedTypes = SUPPORTED_TYPES
) {
    override fun makePredicate(value: Any): GreaterThan<*> {
        return GreaterThan(value as Comparable<*>)
    }

    companion object {
        private val SUPPORTED_TYPES = listOf(
            JsonToken.VALUE_STRING,
            JsonToken.VALUE_NUMBER_FLOAT,
            JsonToken.VALUE_NUMBER_INT,
        )
    }
}

class GreaterThanEqualsDeserializer : AbstractPredicatesDeserializer<GreaterThanEquals<*>>(
    operatorName = "%gte",
    supportedTypes = SUPPORTED_TYPES
) {
    override fun makePredicate(value: Any): GreaterThanEquals<*> {
        return GreaterThanEquals(value as Comparable<*>)
    }

    companion object {
        private val SUPPORTED_TYPES = listOf(
            JsonToken.VALUE_STRING,
            JsonToken.VALUE_NUMBER_FLOAT,
            JsonToken.VALUE_NUMBER_INT,
        )
    }
}

class StartsWithDeserializer : AbstractPredicatesDeserializer<StartsWith>(
    operatorName = "%startsWith",
    supportedTypes = listOf(JsonToken.VALUE_STRING)
) {
    override fun makePredicate(value: Any): StartsWith {
        return StartsWith(value as String)
    }
}

class EndsWithDeserializer : AbstractPredicatesDeserializer<EndsWith>(
    operatorName = "%endsWith",
    supportedTypes = listOf(JsonToken.VALUE_STRING)
) {
    override fun makePredicate(value: Any): EndsWith {
        return EndsWith(value as String)
    }
}