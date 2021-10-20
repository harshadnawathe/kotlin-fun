package com.github.harshadnawathe.proxy

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.HttpMessageReader
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.RequestHandledEvent
import org.springframework.web.reactive.function.BodyExtractor
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.nio.charset.Charset
import java.util.*
import javax.annotation.Resource
import kotlin.random.Random

@SpringBootApplication
class ProxyApplication

@Configuration
class TargetClientConfiguration(
    private val clientBuilder: WebClient.Builder
) {

    private val target = "http://localhost:8081"

    @Bean
    fun targetClient(): WebClient = clientBuilder.baseUrl(target).build()
}

@RestController
class ProxyController {

    @Resource(name = "targetClient")
    private lateinit var target: WebClient

    @RequestMapping(
        path = ["/**"]
    )
    fun proxy(request: ServerHttpRequest): Mono<ResponseEntity<Any?>> {

        return target.method(request.method!!)
            .uri(request.path.value())
            .body(BodyInserters.fromDataBuffers(request.body))
            .exchange()
            .flatMap { response ->
                response.bodyToMono(Any::class.java).map {
                    ResponseEntity.status(response.statusCode())
                        .body(it)
                }
            }
    }
}


@Component
class HttpEventListener  {

    @EventListener
    fun handleApiRequestReceivedEvent(event: RequestHandledEvent) {
        println("[${Thread.currentThread().id}] =============> ${event}")
    }
}

//----------------------------- Test ------------------------------------------------------

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
class ProxyTest {

    private val mockTarget: MockWebServer = MockWebServer().apply {
        dispatcher = QueueDispatcher().apply {
            setFailFast(
                MockResponse()
                    .addHeader("Content-Type", "application/json")
                    .setBody("{ \"ok\": true }")
                    .setResponseCode(200)
            )
        }
    }

    @Autowired
    private lateinit var client: TestRestTemplate


    @BeforeEach
    fun setUp() {
        mockTarget.start(8081)
    }

//    @RepeatedTest(20)
    @Test
    fun `should work with any HttpMethod`() {

        val random = randomHttpMethod()
        println("Method used: ${random.name}")

        val httpEntity = HttpEntity(mapOf("msg" to "Hello!")).takeIf {
            random != HttpMethod.GET && random != HttpMethod.HEAD
        }

        client.exchange("/v1/greeting", random, httpEntity, Any::class.java)

        assertThat(mockTarget.requestCount).isEqualTo(1)
        with(mockTarget.takeRequest()) {
            assertThat(method).isEqualTo(random.name)

            println("${path}")
            println("${method}")
            println(body.readString(Charset.defaultCharset()))
            println("${headers}")
        }

//        JSONAssert.

    }

    private fun randomHttpMethod(): HttpMethod {
        return HttpMethod.values().let { httpMethods ->
            httpMethods[Random.nextInt(httpMethods.size)]
        }
    }

    @AfterEach
    internal fun tearDown() {
        mockTarget.shutdown()
    }
}