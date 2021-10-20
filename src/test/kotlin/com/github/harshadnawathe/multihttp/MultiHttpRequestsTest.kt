package com.github.harshadnawathe.multihttp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class MarkAutoRenewable(
    val fdAccountNumber: String
    //Other properties ignored for now.
)

data class MultiFdUpdateRequest(
    val fdsToBeMarkedAutoRenewable: List<MarkAutoRenewable>
)

data class MultiFdUpdateStatus constructor(
    val fdsMarkedAsAutoRenewable: List<String>,
    val fdsFailed: List<String>
) {
    init {
        require(!(fdsMarkedAsAutoRenewable.isEmpty() && fdsFailed.isEmpty()))
    }

    constructor(fdUpdateStatus: FdUpdateStatus) : this(
        fdsMarkedAsAutoRenewable = when (fdUpdateStatus) {
            is FdUpdateStatus.Success -> listOf(fdUpdateStatus.fdAccountNumber)
            else -> emptyList()
        },
        fdsFailed = when (fdUpdateStatus) {
            is FdUpdateStatus.Failure -> listOf(fdUpdateStatus.fdAccountNumber)
            else -> emptyList()
        }
    )

    val isSuccess: Boolean
        get() = fdsFailed.isEmpty()

    operator fun plus(other: MultiFdUpdateStatus) = MultiFdUpdateStatus(
        fdsMarkedAsAutoRenewable = this.fdsMarkedAsAutoRenewable + other.fdsMarkedAsAutoRenewable,
        fdsFailed = this.fdsFailed + other.fdsFailed
    )
}

sealed class FdUpdateStatus {
    class Success(val fdAccountNumber: String) : FdUpdateStatus()
    class Failure(val fdAccountNumber: String) : FdUpdateStatus()
}


data class FdUpdateResponse(
    val fdAccountNumber: String,
    val status: String
) {
    fun toFdUpdateStatus(): FdUpdateStatus = when (status) {
        "success" -> FdUpdateStatus.Success(fdAccountNumber)
        else -> FdUpdateStatus.Failure(fdAccountNumber)
    }
}

class FdServiceError(val fdAccountNumber: String, val response: ClientResponse) : RuntimeException()

@Component
@ConfigurationProperties(prefix = "fd-service.client.http")
class FdServiceEsbClient(
    webClientBuilder: WebClient.Builder
) {
    lateinit var baseUrl: String
    lateinit var autoRenewUri: String

    private val client by lazy {
        webClientBuilder
            .baseUrl(baseUrl)
            .build()
    }

    private val scheduler = Schedulers.newElastic(this::class.java.simpleName)

    fun update(request: MultiFdUpdateRequest): Mono<MultiFdUpdateStatus> =
        Flux.fromIterable(request.fdsToBeMarkedAutoRenewable)
            .parallel()
            .runOn(scheduler)
            .flatMap { updateSingle(it) }
            .map { MultiFdUpdateStatus(it) }
            .reduce { a, c -> a + c }

    private fun updateSingle(fd: MarkAutoRenewable): Mono<FdUpdateStatus> =
        client.post()
            .uri(autoRenewUri)
            .bodyValue(fd)
            .exchange()
            .timeout(Duration.ofSeconds(3))
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
            .doOnSuccess {
                if (!it.statusCode().is2xxSuccessful) {
                    throw FdServiceError(fd.fdAccountNumber, it)
                }
            }
            .retryWhen(
                Retry.max(3).filter {
                    it is FdServiceError && it.response.statusCode().is5xxServerError
                }
            )
            .flatMap { it.bodyToMono<FdUpdateResponse>() }
            .map { it.toFdUpdateStatus() }
            .onErrorReturn(FdUpdateStatus.Failure(fd.fdAccountNumber)) //finally call it a failure
}

// Debug logging
class WebClientLoggingFilter : ExchangeFilterFunction {
    private val log: Logger = LoggerFactory.getLogger(WebClientLoggingFilter::class.java)

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        Mono.fromCallable {
            Random.nextInt().also { id ->
                log.debug("[$id] Sending Http request to ${request.url()}")
            }
        }.flatMap { id ->
            forwardAndLog(next, request, id)
        }

    private fun forwardAndLog(
        next: ExchangeFunction,
        request: ClientRequest,
        id: Int?
    ) = next.exchange(request)
        .doOnSuccess {
            log.debug("[$id] Received response with status ${it.rawStatusCode()}")
        }
        .doOnError {
            log.error("[$id] Error ${it::class.java.simpleName}\n${it.stackTrace}")
        }
}

@Configuration
class WebClientConfiguration {

    @Bean
    fun webClientCustomizer() = WebClientCustomizer { webClientBuilder ->
        webClientBuilder.filter(WebClientLoggingFilter())
    }

}


//---------------------------------------------------------------

@SpringBootApplication
class TestApplication

@SpringBootTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(
    properties = [
        "fd-service.client.http.base-url=http://localhost:5687/api",
        "fd-service.client.http.auto-renew-uri=/v1/accounts/FD/auto-renew",
        "logging.level.com.github.harshadnawathe.multihttp=DEBUG"
    ]
)
class MultiHttpRequestsTest {

    @Autowired
    lateinit var fdServiceEsbClient: FdServiceEsbClient

    private val mockTarget: MockWebServer = MockWebServer().apply {
        dispatcher = FdServiceDispatcher()
    }

    @BeforeEach
    fun setUp() {
        mockTarget.start(5687)
    }

    @Test
    fun `should call ESB service until success`() {
        val status = fdServiceEsbClient.update(
            MultiFdUpdateRequest(
                listOf(
                    MarkAutoRenewable("123"), //Pass in second attempt
                    MarkAutoRenewable("456"), //Pass in first attempt
                    MarkAutoRenewable("AlwaysFail"), //Bad request so always fail
                    MarkAutoRenewable("Timeout"), //Timeout in first attempt but pass in second
                    MarkAutoRenewable("BadResponse") //Always fail because of response parsing
                )
            )
        ).block()

        println("$status")
    }
}

class FdServiceDispatcher : Dispatcher() {

    private val counts = ConcurrentHashMap<String, Int>()

    override fun dispatch(request: RecordedRequest) =
        request.body
            .readByteArray()
            .let {
                mapper.readValue<Map<String, String>>(it)
            }["fdAccountNumber"]
            ?.also { fdAccountNumber ->
                updateCountFor(fdAccountNumber)
            }
            ?.let { fdAccountNumber ->

                if (fdAccountNumber.startsWith("BadResponse")) {
                    return@let badResponse(fdAccountNumber)
                }

                if (fdAccountNumber.startsWith("Timeout")) {
                    if (counts.getValue(fdAccountNumber) == 1) {
                        println("Server is too busy.. it will take some time to respond ...")
                        Thread.sleep(5000)
                    }
                    return@let successResponseFor(fdAccountNumber)
                }

                val code = fdAccountNumber.toIntOrNull() ?: return@let badRequest()

                if (counts.getValue(fdAccountNumber) > (code % 2)) {
                    successResponseFor(fdAccountNumber)
                } else {
                    serverError()
                }
            }
            ?: badRequest()

    private fun updateCountFor(fdAccountNumber: String) {
        counts.compute(fdAccountNumber) { _, count ->
            count?.let { it + 1 } ?: 1
        }
    }

    private fun successResponseFor(fdAccountNumber: String) =
        MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody("{ \"status\": \"success\", \"fdAccountNumber\":\"$fdAccountNumber\" }")
            .setResponseCode(200)

    private fun badResponse(fdAccountNumber: String) =
        MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody("{ \"success\": true, \"accountNumber\":\"$fdAccountNumber\" }")
            .setResponseCode(200)

    private fun serverError() =
        MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody("{ \"status\": \"failed\", \"error\":\"Crappy server failed to process this request\" }")
            .setResponseCode(500)

    private fun badRequest() =
        MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody("{ \"status\": \"failed\", \"error\":\"Invalid request body\" }")
            .setResponseCode(400)

    companion object {
        private val mapper = jacksonObjectMapper()
    }

}