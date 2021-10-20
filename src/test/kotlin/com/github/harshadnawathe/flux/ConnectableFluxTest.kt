package com.github.harshadnawathe.flux

import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

class ConnectableFluxTest {

    fun evenOddSeq(nums: Flux<Int>): Tuple2<Flux<String>, Flux<String>> {
        val publisher = nums.publish().autoConnect(2)

        return Tuples.of(
            publisher.filter { it % 2 == 0 }.map { "EVEN $it" },
            publisher.filter { it % 2 == 1 }.map { "ODD $it" }
        )
    }

    fun evenOddParallel(nums: Flux<Int>): Tuple2<Flux<String>, Flux<String>> {
        val publisher = nums.publish()

        val even = Sinks.many().unicast().onBackpressureBuffer<String>()
        val evenFlux = publisher.filter { it % 2 == 0 }.map { "EVEN $it" }
            .doOnNext {
                even.tryEmitNext(it)
            }
            .doOnComplete {
                even.tryEmitComplete()
            }

        val odd = Sinks.many().unicast().onBackpressureBuffer<String>()
        val oddFlux = publisher.filter { it % 2 == 0 }.map { "ODD $it" }
            .doOnNext {
                odd.tryEmitNext(it)
            }
            .doOnComplete {
                odd.tryEmitComplete()
            }

        return Tuples.of(
            even.asFlux().doOnSubscribe {
                evenFlux.subscribe()
                publisher.connect()
            },
            odd.asFlux().doOnSubscribe {
                oddFlux.subscribe()
                publisher.connect()
            },
        )
    }

    @Test
    fun `without sink`() {
        val (even, odd) = evenOddSeq(Flux.range(1, 10))

        even.subscribe { println(it) }
        odd.subscribe { println(it) }

        /*Produces
ODD 1
EVEN 2
ODD 3
EVEN 4
ODD 5
EVEN 6
ODD 7
EVEN 8
ODD 9
EVEN 10
        */


    }

    @Test
    fun `with sink`() {
        val (even, odd) = evenOddParallel(Flux.range(1, 10))

        even.subscribe { println(it) }
        odd.subscribe { println(it) }
        /*Produces
EVEN 2
EVEN 4
EVEN 6
EVEN 8
EVEN 10
ODD 2
ODD 4
ODD 6
ODD 8
ODD 10
         */
    }
}