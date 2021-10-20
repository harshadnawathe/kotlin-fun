package com.github.harshadnawathe.auth.refresh

import com.github.harshadnawathe.util.PathMatchingDispatcher
import com.github.harshadnawathe.util.enqueue
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootApplication
class Application

@SpringBootTest
class TokenAutoRenewalTest {

    @Autowired
    lateinit var orderServiceClient: OrderServiceClient

    private val mockOrderServer = MockWebServer().apply {
        dispatcher = PathMatchingDispatcher()
    }

    @SpykBean
    lateinit var authService: AuthService

    @BeforeEach
    fun orderServerStart() {
        mockOrderServer.start(5000)
    }

    @AfterEach
    fun orderServerShutdown() {
        mockOrderServer.shutdown()
    }

    private val successResponse = MockResponse().addHeader("Content-Type", "application/json")
        .setBody("""{"orderId": "ORD1234"}""")
        .setResponseCode(200)


    private val unauthorizedResponse = MockResponse().addHeader("Content-Type", "application/json")
        .addHeader(
            "WWW-Authenticate",
            "Bearer realm=\"order-service\", error=\"invalid_token\", error_description=\"The access token expired\""
        )
        .setBody("Unauthorized")
        .setResponseCode(401)

    @Test
    fun `should send service access token in auth header`() {
        mockOrderServer.enqueue(
            path = "/order-service/v1/orders/ORD1234",
            successResponse
        )

        orderServiceClient.getOrder("ORD1234").block()

        val authorization = mockOrderServer.takeRequest().getHeader("Authorization")
        assertThat(authorization).matches("Bearer service.access.token-\\d+")
    }

    private val notFoundResponse = MockResponse().addHeader("Content-Type", "application/json")
        .setBody("Not found")
        .setResponseCode(404)

    @Test
    fun `should renew token `() {
        mockOrderServer.enqueue(
            path = "/order-service/v1/orders/ORD1234",
            unauthorizedResponse,
            unauthorizedResponse,
            successResponse
        )

        orderServiceClient.getOrder("ORD1234").block()

        assertThat(mockOrderServer.requestCount).isEqualTo(3)
    }

    @Test
    fun `should throw Unauthorized`() {
        mockOrderServer.enqueue(
            path = "/order-service/v1/orders/ORD1234",
            unauthorizedResponse
        )

        val orderOp = orderServiceClient.getOrder("ORD1234")

        StepVerifier.create(orderOp)
            .expectError(WebClientResponseException.Unauthorized::class.java)
            .verify()
    }

    @Test
    fun `should throw NotFound`() {
        mockOrderServer.enqueue(
            path = "/order-service/v1/orders/ORD1234",
            notFoundResponse,
        )

        val orderOp = orderServiceClient.getOrder("ORD1234")

        StepVerifier.create(orderOp)
            .expectError(WebClientResponseException.NotFound::class.java)
            .verify()
    }

    @Test
    fun `should throw Exception`() {
        every { authService.token(any()) } answers { Mono.error(RuntimeException("some random error")) }

        val orderOp = orderServiceClient.getOrder("ORD1234")

        StepVerifier.create(orderOp)
            .expectErrorSatisfies {
                assertThat(it).isInstanceOf(RuntimeException::class.java)
                    .hasMessage("some random error")
            }
            .verify()
    }
}