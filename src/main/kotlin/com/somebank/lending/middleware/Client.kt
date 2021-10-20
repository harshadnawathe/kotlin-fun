package com.somebank.lending.middleware

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class Client {

    final var count = 0
        private set

    fun reset() {
        count = 0
    }

    fun get() : Mono<Int> = Mono.fromCallable {
        ++count
    }
}