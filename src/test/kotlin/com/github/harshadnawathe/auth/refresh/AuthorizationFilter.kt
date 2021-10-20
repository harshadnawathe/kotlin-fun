package com.github.harshadnawathe.auth.refresh

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration


@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class AuthorizationFilter(
    private val auth: AuthService
) : ExchangeFilterFunction {

    private class UnauthorizedAccess(val response: ClientResponse) : RuntimeException("Unauthorized API access")

    private val tokenId = "fooBar"

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
        return auth.token(tokenId)
            .map { token ->
                request.withAuthorization(token)
            }
            .flatMap {
                next.exchange(it)
            }
            .doOnSuccess {
                checkUnauthorized(it)
            }
            .retryWhen(
                unauthorizedAccessIsThrown()
            )
            .onErrorResume {
                responseOrError(it)
            }
    }

    private fun responseOrError(error: Throwable) = if (error is UnauthorizedAccess) {
        Mono.just(error.response)
    } else {
        Mono.error(error)
    }

    private fun unauthorizedAccessIsThrown() = Retry
        .backoff(2, Duration.ofSeconds(1))
        .filter { it is UnauthorizedAccess }
        .doBeforeRetry { auth.clearCache(tokenId) }
        .onRetryExhaustedThrow { _, signal ->
            LOG.info("retries exhausted")
            signal.failure()
        }


    private fun checkUnauthorized(response: ClientResponse) {
        if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
            throw UnauthorizedAccess(response)
        }
    }

    private fun ClientRequest.withAuthorization(token: String): ClientRequest {
        LOG.debug("setting bearer auth with token: $token")
        return ClientRequest.from(this).headers { it.setBearerAuth(token) }.build()
    }

    companion object {

        @JvmStatic
        private val LOG = LoggerFactory.getLogger(AuthorizationFilter::class.java)
    }
}