package com.github.harshadnawathe.flux

import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono

fun process(nums: IntRange): Mono<String> {
    return nums.toFlux()
        .doOnEach {
            println("==>" + it.contextView.getOrDefault("traceId", 0))
        }
        .parallel()
        .runOn(Schedulers.parallel())
        .map { it.toString() }
        .doOnEach {
            println("-->" + it.contextView.getOrDefault("traceId", 0))
        }
        .reduce { a, b -> a + b }

}


fun nums(start: Int, endInclusive: Int): Mono<IntRange> {
    return IntRange(start, endInclusive).toMono()
}


class ContextTest {


    @Test
    fun `context should be available in flux chain`() {
        val r = nums(1, 4)
            .flatMap { process(it) }
            .contextWrite { it.put("traceId", 1234) }
            .block()

        println(r)
    }
}