package com.github.harshadnawathe.logging

import net.logstash.logback.argument.StructuredArguments.v
import net.logstash.logback.marker.Markers
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.*
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.CoreSubscriber
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.publisher.Operators
import reactor.test.StepVerifier
import reactor.util.context.Context
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.random.Random

@RestController
@RequestMapping(path = ["/v1"])
class Controller(private val service: Service) {

    @GetMapping("/this/{id}")
    fun getThis(@PathVariable id: String): Mono<Map<*, *>> {
        log.debug("Received call for this with id: {}", v("id", id))
        return service.getThis(id).contextWrite {
            it.putToMdc("thisId", id)
        }
    }

    companion object {
        @JvmStatic
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val log = LoggerFactory.getLogger(javaClass.enclosingClass)
    }
}

@Component
class Service(private val thatClient: ThatClient) {
    fun getThis(id: String): Mono<Map<*, *>> {
        val marker = Markers.append("id", id)
        log.debug(marker, "Computing this from that")
        return thatClient.getThat(id).doOnSuccess {
            log.debug(marker, "Done computing this")
        }
    }

    companion object {
        @JvmStatic
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val log = LoggerFactory.getLogger(javaClass.enclosingClass)
    }
}

class ThatClientException(reason: String, cause: Throwable? = null) : RuntimeException(reason, cause)

@Component
class ThatClient(
    @Qualifier("thatWebClient")
    private val client: WebClient
) {

    fun getThat(id: String): Mono<Map<*, *>> {
        log.debug("Fetching that with id: {}", v("id", id))

        fun handle(it: ClientResponse) : Mono<Map<*,*>> {
            if (it.statusCode().value() == 404) {
                throw ThatClientException("Invalid id: $id, Not found").also {
                    log.warn(Markers.append("id", id), "That with id {} not found", v("id", id))
                }
            }

            if (it.statusCode().isError) {
                throw ThatClientException("Server side error").also {
                    log.error(Markers.append("id", id), "Remote service responded with an error")
                }
            }

            return it.bodyToMono(Map::class.java)
                .onErrorMap { error ->
                    log.error(Markers.append("id", id), "Cannot deserialize response from server", error)
                    ThatClientException("Deserialization failed", error)
                }
                .doOnSuccess { map ->
                    log.debug(
                        Markers.append("data", map["data"]),
                        "Received data from that-service for id: {}", v("id", id)
                    )
                }
        }

        return client.get()
            .uri("/v1/that/{id}", id)
            .exchangeToMono(::handle)
            .onErrorMap {
                log.error(Markers.append("id", id), "Error while calling that-service", it)
                ThatClientException("that-service error", it)
            }
    }

    companion object {
        @JvmStatic
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val log = LoggerFactory.getLogger(javaClass.enclosingClass)
    }
}

@ConstructorBinding
@ConfigurationProperties(prefix = "service.that.http")
class ThatClientProperties(
    var baseUrl: String
)

@Configuration
@EnableConfigurationProperties(ThatClientProperties::class)
class ThatClientConfiguration(
    val properties: ThatClientProperties
) {

    @Bean
    fun thatWebClient(builder: WebClient.Builder): WebClient =
        builder.baseUrl(properties.baseUrl).build()
}

// ==================================================//

private class LoggingMdc : HashMap<String, String>()

fun Context.putToMdc(key: String, value: String): Context {
    val mdc = this.getOrDefault(LoggingMdc::class.java, LoggingMdc())!!
    mdc[key] = value
    return put(LoggingMdc::class.java, mdc)
}


//var count = 0

private fun Context.copyToMdc() {
//    count++
    val mdc: Map<String, String>? = this.getOrDefault(LoggingMdc::class.java, null)
    when {
        mdc != null -> MDC.setContextMap(mdc)
        else -> MDC.clear()
    }
}

class MdcLifter<T>(private val subscriber: CoreSubscriber<T>) : CoreSubscriber<T> {

    override fun onNext(t: T) {
        subscriber.currentContext().copyToMdc()
        subscriber.onNext(t)
    }

    override fun onError(t: Throwable?) {
        subscriber.currentContext().copyToMdc()
        subscriber.onError(t)
    }

    override fun onSubscribe(s: Subscription) {
        subscriber.onSubscribe(s)
    }

    override fun onComplete() {
        subscriber.onComplete()
    }

    override fun currentContext(): Context {
        return subscriber.currentContext()
    }

}

class MdcTraceIdInsertingWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return chain.filter(exchange)
            .contextWrite { ctx ->
                exchange.request.headers["X-TRACE-ID"]
                    ?.firstOrNull()
                    ?.let {
                        ctx.putToMdc("traceId", it)
                    } ?: ctx
            }
    }
}

class MdcTraceIdInsertingFilterFunction : ExchangeFilterFunction {

    private val lookUp = mapOf("traceId" to "X-TRACE-ID")

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
//        request.headers().add()
        return Mono.deferContextual { context ->
            val mdc: Map<String, String>? = context.getOrDefault(LoggingMdc::class.java, null)
            mdc?.mapKeys { (k, _) ->
                lookUp[k] ?: k
            }?.forEach { k, v ->
                request.headers().add(k, v)
            }
            next.exchange(request);
        }
    }
}

@Configuration
class MdcLifterConfiguration {
    companion object {
        val MDC_CONTEXT_REACTOR_KEY: String = MdcLifterConfiguration::class.java.name
    }

    @Bean
    fun mdcTraceIdInsertingWebFilter() = MdcTraceIdInsertingWebFilter()

    @PostConstruct
    fun contextOperatorHook() {
        Hooks.onEachOperator(
            MDC_CONTEXT_REACTOR_KEY,
            Operators.lift { _, subscriber -> MdcLifter(subscriber) }
        )
    }

    @PreDestroy
    fun cleanupHook() {
        Hooks.resetOnEachOperator(MDC_CONTEXT_REACTOR_KEY)
    }
}

// ==================================================//


// ==================================================//

@SpringBootApplication
class Application

//===================================================//

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
@TestPropertySource(
    properties = [
        "service.that.http.base-url=http://localhost:4567/that-service",
        "logging.level.com.github.harshadnawathe.logging=DEBUG"
    ]
)
@AutoConfigureWebTestClient
class ReactiveApplicationLoggingMdcTest {

    @Autowired
    lateinit var testClient: WebTestClient

    @Autowired
    lateinit var thatClient: ThatClient


    private val mockThatServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return success()
                return when {
                    path.endsWith("that-not-found") -> notFound(request)
                    path.endsWith("that-server-error") -> serverError()
                    path.endsWith("bad-response-body") -> badResponseBody()
                    else -> success()
                }
            }

            private fun badResponseBody(): MockResponse {
                return MockResponse().addHeader("Content-Type", "application/xml")
                    .setResponseCode(200)
                    .setBody("<data>42</data>")
            }

            private fun success() = MockResponse()
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200)
                .setBody("{\"data\": 42 }")

            private fun notFound(request: RecordedRequest) = MockResponse()
                .addHeader("Content-Type", "application/json")
                .setResponseCode(404)
                .setBody("{\"notFound\": \"${request.path}\" }")

            private fun serverError() = MockResponse()
                .addHeader("Content-Type", "application/json")
                .setResponseCode(500)
                .setBody("{ \"details\": \"Some server side problem\"}")
        }
    }

    @BeforeEach
    fun setUp() {
        mockThatServer.start(4567)
    }

    @Test
    fun `should work 1`() {

        testClient.get()
            .uri("/v1/this/1234")
            .headers {
                it.add("X-TRACE-ID", "trace-${Random.nextInt(0, 1000)}")
            }
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody().json("{\"data\": 42}")

    }

    @Test
    fun `should work 2`() {
        testClient.get()
            .uri("/v1/this/that-not-found")
            .headers {
                it.add("X-TRACE-ID", "trace-${Random.nextInt(0, 1000)}")
            }
            .exchange()
            .expectStatus().is5xxServerError
    }

    @Test
    fun `should work 3`() {
        testClient.get()
            .uri("/v1/this/that-server-error")
            .headers {
                it.add("X-TRACE-ID", "trace-${Random.nextInt(0, 1000)}")
            }
            .exchange()
            .expectStatus().is5xxServerError
    }

    @Test
    fun `should work 4`() {
        testClient.get()
            .uri("/v1/this/bad-response-body")
            .headers {
                it.add("X-TRACE-ID", "trace-${Random.nextInt(0, 1000)}")
            }
            .exchange()
            .expectStatus().is5xxServerError
    }

    @Test
    fun `should work 5`() {
        mockThatServer.shutdown()

        testClient.get()
            .uri("/v1/this/4567")
            .headers {
                it.add("X-TRACE-ID", "trace-${Random.nextInt(0, 1000)}")
            }
            .exchange()
            .expectStatus().is5xxServerError
    }

    @Test
    fun `should work 6`() {
        val mono = Mono.just(0)
        val wrapCount = 100
        val result = (1..wrapCount).fold(mono) { acc, curr ->
            acc.flatMap {
                thatClient.getThat("$curr").thenReturn(curr)
            }
        }.contextWrite {
            it.putToMdc("traceId", "some-trace-id")
                .putToMdc("spanId", "some-span-id")
        }
        StepVerifier.create(result)
            .expectNext(wrapCount)
            .verifyComplete()
    }

//    @Test
//    fun `should work 7`() {
//        count=0
//        val mono = Mono.just(0)
//        val wrapCount = 100
//        (1..wrapCount).fold(mono) { acc, curr ->
//            acc.flatMap {
//                thatClient.getThat("$curr").thenReturn(curr)
//            }
//        }.block()
//        println("$count")
//    }

    @Test
    fun benchMarkTest() {
        val durations = (1..25).map {
            val start = System.nanoTime()
            `should work 6`()
            val end = System.nanoTime()
            end - start
        }
        val x = 1000000L

        println("Min: ${durations.min()?.div(x)} , Max: ${durations.max()?.div(x)}, Avg: ${durations.average().div(x)}")
    }

    private val mdc = LoggingMdc().apply {
        repeat(5) {
            put(RandomStringUtils.randomAlphabetic(5, 10), RandomStringUtils.randomAlphabetic(16))
        }
    }

    val ctx = Context.of(
        mapOf(
            "foo" to "bar",
            LoggingMdc::class.java to mdc
        )
    )

    @Test
    fun benchMarkTestCopyToMdc() {
        val durations = (1..10000).map {
            val start = System.nanoTime()
            ctx.copyToMdc()
            val end = System.nanoTime()
            end - start
        }

        println("Min: ${durations.minOrNull()} , Max: ${durations.maxOrNull()}, Avg: ${durations.average()}")
    }

    @AfterEach
    fun tearDown() {
        mockThatServer.shutdown()
    }

    @Test
    fun `just test`() {
        val lst = listOf(1, 2, 3)


    }
}
