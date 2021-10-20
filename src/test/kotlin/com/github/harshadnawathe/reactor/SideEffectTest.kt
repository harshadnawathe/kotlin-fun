package com.github.harshadnawathe.reactor

import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class SideEffectTest {

    @Test
    fun `should log`() {
        val x = Mono.fromCallable {
            Thread.sleep(1000)
            "Hello"
        }.doOnSuccess {
            Mono.subscriberContext().subscribe()
        }.doOnError {

        }.map {
            println("map $it")
            it.toUpperCase()
        }.doOnSuccess {
            Thread.sleep(1000)
            println("doOnSuccess 2 $it")
        }.onErrorResume {
            println("onErrorResume $it")
            Mono.just("Ok")
        }.log().block()

        println(x)
    }
}