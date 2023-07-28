package com.github.harshadnawathe.jackson.parse.expressiontree

import com.github.harshadnawathe.jackson.parse.expressiontree.jackson.predicatesJacksonModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// -- Predicates --


// ---

class Employee(
    val name: String,
    val title: String,
    val salary: Double
)

class SearchCriteriaTest {

    @Test
    fun `should work`() {
        val employee = Employee("Harshad", "Dev", 000.0)

        //val nameMatcher = EmployeeMapperPredicate(Employee::name, Equals("Harshad"))
        val nameMatcher = MapperPredicate(
            Employee::name,
            AllOf(
                listOf(StartsWith("Har"), EndsWith("ad"))
        )
        )
        //val titleMatcher = MapperPredicate(Employee::title, Equals("Dev"))
        val titleMatcher = ReflectionBasedMapperPredicate("title", Equals("Dev"))
        val salaryMatcher = MapperPredicate(Employee::salary, LessThan(1000.0))

        var b: Boolean
        b = nameMatcher.test(employee)
        assertThat(b).isTrue()

        val allOf : Predicate<Employee> =  AllOf(listOf(nameMatcher, titleMatcher, salaryMatcher))

        b = allOf.test(employee)
        assertThat(b).isTrue()
    }

    @Test
    fun `should parse property matching expression`() {
        val mapper = jacksonObjectMapper()
        mapper.registerModule(predicatesJacksonModule())

        val json = """{
            |   "%any": [
            |       {
            |          "name": { 
            |               "%all" : [
            |                   { "%startsWith": "Ha" },
            |                   { "%endsWith": "ad" }
            |               ]
            |          }
            |       },
            |       {
            |           "salary" : {
            |               "%gt" : 100.0
            |           }
            |       }   
            |   ]
            |}""".trimMargin()

        val p = mapper.readValue(json, jacksonTypeRef<Predicate<Employee>>())

        val employee = Employee("Harshad", "Dev", 1000.0)

        assertThat(p.test(employee)).isTrue()
    }
}

class SearchCriteriaParser {
    val mapper = jacksonObjectMapper()

    private val reducers = listOf("\$all", "\$any")
    private val proerties = listOf("name", "title", "salary")
    private val predicates = listOf("\$eq", "\$neq", "\$lt", "\$lte", "\$gt", "\$gte", "\$startsWith", "\$endsWith")



    fun parse(json: String) : Predicate<Employee> {
        val jsonNode = mapper.readTree(json)
        require(jsonNode.isObject) {
            "unexpected node type: ${jsonNode.nodeType}"
        }

        return parsePropertyMatcherOrReducer(jsonNode as ObjectNode)
    }

    fun parsePropertyMatcherOrReducer(node: ObjectNode) : Predicate<Any> {
        require(node.size() == 1) {
            "expect exactly one key in the node"
        }

        val key = node.fieldNames().next()
        val nextNode = node.get(key)

        if(key in reducers) {
            require(nextNode.isArray)
            return parseReducer(nextNode as ArrayNode)
        }

        if(key in proerties) {
            require(nextNode.isObject)
            return parseReducerOrPredicate(nextNode as ObjectNode)
        }

        throw IllegalArgumentException("unexpected key: $key")
    }

    fun parseReducerOrPredicate(node: ObjectNode) : Predicate<Any> {
        TODO()
    }

    fun parseReducer(node: ArrayNode) : Predicate<Any> {
        TODO()
    }
}





