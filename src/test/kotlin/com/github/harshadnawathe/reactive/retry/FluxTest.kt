package com.github.harshadnawathe.reactive.retry

import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.time.Duration
import kotlin.random.Random

class FluxTest {

    @Test
    fun `should work`() {

        Flux.fromIterable(IntRange(0,100).toList())
            .flatMapSequential {
                Mono.fromCallable {
                    println("Processing $it")
                    it
                }.delayElement(
                    Duration.ofSeconds(Random.nextInt(2).toLong())
                ).flatMap {
                    if(Random.nextInt() % 10 == 0)
                        Mono.error(RuntimeException())
                    else
                        it.toMono()
                }.onErrorResume {
                    Mono.just(-1)
                }
            }
            .flatMap {
                Mono.fromCallable {
                    println("Committing $it")
                    it
                }.delayElement(
                    Duration.ofSeconds(Random.nextInt(5).toLong())
                )
            }
            .map {
                it * 100
            }.collectList()
            .block()
    }

    @Test
    fun `should work with concatMap`() {
        Flux.fromIterable(listOf(1,2,3))
            .doOnNext {
                if(it % 2 == 0) {
                    throw RuntimeException("Found event number: $it")
                }
            }
            .concatMap { x -> Flux.fromIterable(listOf(x, x*2)) }
            .onErrorContinue { _, _ -> }
            .map {
                it * 10
            }
            .subscribe(
                { n ->
                    println(n)
                },
                {e ->
                    println(e.message)
                }
            )
    }

    @Test
    fun `should work with flatMapSequential`() {
        TODO("Not yet implemented")
    }
}