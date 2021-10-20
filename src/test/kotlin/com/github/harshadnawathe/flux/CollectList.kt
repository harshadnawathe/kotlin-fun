package com.github.harshadnawathe.flux

import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.lang.RuntimeException

class CollectListTest {


    @Test
    fun `should work`() {

        mutableListOf(1,2,3)
            .someFun()
            .map { it.size }
            .subscribe(
                { println(it) },
                { println(it) }
            )
    }
}

fun List<Int>.someFun() =   Flux.fromIterable(this)
    .flatMap {
        if(it == 3 ) {
            Mono.error(RuntimeException("Wrong number $it"))
        } else {
            Mono.just(it * 2)
        }
    }
    .collectList()

class RandomMonoTest {

    @Test
    fun `should work`() {
        Mono.just(5)
            .log()
            .filter{
                it % 2 == 0
            }
            .switchIfEmpty {
                Mono.just(-1)
            }
            .subscribe{
                println("Final: $it")
            }


    }
}