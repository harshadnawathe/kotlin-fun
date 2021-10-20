package com.github.harshadnawathe.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.schibsted.spt.data.jslt.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import reactor.test.StepVerifier
import reactor.util.function.component1
import reactor.util.function.component2
import javax.annotation.PostConstruct

@JsonSerialize(using = Request.Serializer::class)
class Request<T : Any>(
    private val type: Class<out T>,
    private val json: JsonNode
) {
    constructor(value: T) : this(
        type = value::class.java,
        json = mapper.valueToTree<JsonNode>(value)
    )

    constructor(type: Class<T>, jsonText: String) : this(
        type,
        json = mapper.readTree(jsonText)
    )

    //Merge operations
    operator fun rem(override: Any) = Request(type = type, json = bodyWith(override))

    operator fun rem(override: Mono<*>): Mono<Request<T>> = override.map { this % it }

    private fun bodyWith(override: Any): JsonNode = when (override) {
        is Request<*> -> bodyWith(override.json)
        is Mono<*> -> throw IllegalArgumentException("Cannot combine with erased reactive type")
        else -> updatedValue(json, override)
    }

    companion object ValueUpdater {
        //TODO: Make this init once
        private var mapper: ObjectMapper = jacksonObjectMapper().setDefaultMergeable(true)
            set(value) {
                field = value.setDefaultMergeable(true)
            }

        fun updatedValue(basic: JsonNode, override: Any): JsonNode = mapper.updateValue(basic, override)
    }

    //TODO: Introduce auto-configuration
    class MapperInjector(
        private val mapper: ObjectMapper
    ) {
        @PostConstruct
        fun injectMapper() {
            ValueUpdater.mapper = mapper
        }
    }

    //Json Conversion
    val body: T by lazy { mapper.treeToValue(json, type) }

    val jsonBody: JsonNode by lazy { body.let { json } }

    val isValid: Boolean by lazy {
        try { //validation by trial and error
            body.let { true }
        } catch (_: JsonMappingException) {
            false
        }
    }

    class Serializer : JsonSerializer<Request<*>>() {
        private val log = LoggerFactory.getLogger(this::class.java)

        override fun serialize(value: Request<*>, gen: JsonGenerator, serializers: SerializerProvider) {
            log.debug("Tree used for final conversion: $value")
            gen.writeObject(value.jsonBody)
        }
    }

    //Debugging
    override fun toString(): String = jsonText

    private val jsonText: String by lazy {
        mapper.writeValueAsString(json)
    }

}

// Extensions to handle reactive Requests
operator fun <T : Any> Mono<Request<T>>.rem(override: Any): Mono<Request<T>> = map { it % override }

operator fun <T : Any> Mono<Request<T>>.rem(override: Mono<*>): Mono<Request<T>> =
    Mono.zip(this, override).map { (r, o) -> r % o }

// ---------------------------------------------------


data class CustomerRequest(
    val name: Name,
    val occupation: Occupation,
    val country: String,
    val scheme: Scheme
) {
    @JsonInclude(NON_NULL)
    data class Name(
        val firstName: String,
        val middleName: String? = null,
        val lastName: String
    )

    enum class Occupation {
        SALARIED,
        NON_SALARIED
    }

    data class Scheme(
        val code: String,
        val type: String
    )
}

data class Name(
    val fn: String,
    val ln: String
)

data class CustomerOccupation(
    val occupation: String
)

class JsltTests {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `should build CustomerRequest from parts`() {
        //Basic request with all defaults (json text can come from resources)
        val basic = Request(
            CustomerRequest::class.java,
            """{
                | "country": "USA",
                | "scheme": {
                |   "code": "Scheme_123",
                |   "type": "AWESOME_SCHEME"
                | }
                |}""".trimMargin()
        )

        //Environment specific configuration coming from seed DB
        val seedConfig = Mono.fromCallable {
            mapOf("scheme" to mapOf("code" to "Scheme_ABC"))
        }

        //Domain object mapped using JSLT forms request part
        val name = Parser.compileString(
            """{ "name" : {  "firstName": .fn, "lastName": .ln } }"""
        ).apply(mapper.valueToTree(Name("Peter", "Parker")))

        //Domain object used directly as request part
        val occupation = CustomerOccupation("NON_SALARIED").toMono()

        StepVerifier.create(
            //Combine all to generate final request.
            basic % seedConfig % name % occupation
        ).expectNextMatches {
            it.body == CustomerRequest(
                name = CustomerRequest.Name(firstName = "Peter", lastName = "Parker"),
                occupation = CustomerRequest.Occupation.NON_SALARIED,
                country = "USA",
                scheme = CustomerRequest.Scheme(type = "AWESOME_SCHEME", code = "Scheme_ABC")
            )
        }.verifyComplete()
    }

    @Test
    fun `should fail when mandatory fields are not injected through parts`() {
        //Basic request with all defaults (json text can come from resources)
        val basic = Request(
            CustomerRequest::class.java,
            """{
                | "country": "USA",
                | "scheme": {
                |   "code": "Scheme_123",
                |   "type": "AWESOME_SCHEME"
                | }
                |}""".trimMargin()
        )

        val name = CustomerRequest.Name(
            firstName = "Bruce",
            middleName = "Thomas",
            lastName = "Wayne"
        )

        //missing occupation
        val request = basic % name

        assertThat(request.isValid).isFalse()

        assertThrows<JsonMappingException> {
            mapper.writeValueAsString(request)
        }
    }


    @Test
    fun jsltTest() {
        val expr = Parser.compileString(
            """{
            | "applicants": [
            |    {
            |      "name": join([.firstName, .lastName], " ")
            |    }
            | ]
            |}""".trimMargin()
        )

        val name = CustomerRequest.Name(firstName = "Peter", lastName = "Parker")

        val result = expr.apply(
            mapper.valueToTree(name)
        )

        println(mapper.writeValueAsString(result))
    }

    @Test
    fun jsltTest2() {
        val input = mapOf(
            "name" to mapOf(
                "firstName" to "Bruce",
                "lastName" to "Wayne"
            ),
            "offerCode" to 400
        )
        val config = mapOf(
            "offerCode" to 200
        )

        val factory = RequestFactory(mapper)

        val jsonNode = factory.requestFrom(input, config)

        println(mapper.writeValueAsString(jsonNode))
    }

}

class RequestFactory(private val mapper: ObjectMapper) {
    private val expression = Parser.compileResource("request.jslt")

    fun requestFrom(input: Any, config: Any) : JsonNode {
        val context = mapOf("input" to input, "config" to config)

        return expression.apply(mapper.valueToTree(context))
    }
}