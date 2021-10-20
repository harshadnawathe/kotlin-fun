package com.github.harshadnawathe.reactive.retry

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.util.context.Context
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration
import javax.annotation.PostConstruct


fun  <T> Mono<T>.retryIf(condition: (T) -> Boolean) : Mono<T> {

    val retryConfig = RetryConfig
    return this.map {
        it.also { x ->
            require(condition(x).not())
        }
    }.retryWhen(
        Retry.backoff(retryConfig.numRetries, retryConfig.firstBackOff)
            .jitter(retryConfig.jitterFactor)
            .maxBackoff(retryConfig.maxBackOff)
    )
}

@Component
@ConfigurationProperties(prefix = "middleware.client.retry")
object RetryConfig {
    var numRetries: Long = 3
    var firstBackOff: Duration = Duration.ofSeconds(5)
    var maxBackOff: Duration = Duration.ofSeconds(30)
    var jitterFactor: Double = 0.5
}