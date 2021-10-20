package com.github.harshadnawathe.auth.refresh

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class OrderServiceClient(
    webClientBuilder: WebClient.Builder,
    authorizationFilter: AuthorizationFilter
) {
    private val webClient = webClientBuilder
        .baseUrl("http://localhost:5000/order-service/v1")
        .filter(authorizationFilter)
        .build()

    fun getOrder(orderId: String): Mono<Order> {
        return webClient.get()
            .uri("/orders/{orderId}", orderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchangeToMono {
                it.toOrder()
            }
    }

    private fun ClientResponse.toOrder() =
        if (statusCode().isError) {
            createException().flatMap { Mono.error(it) }
        } else {
            bodyToMono(Order::class.java)
        }
}