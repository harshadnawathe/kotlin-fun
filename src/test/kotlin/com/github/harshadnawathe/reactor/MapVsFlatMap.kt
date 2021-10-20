package com.github.harshadnawathe.reactor

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.util.*


class AppleJuice

class Apple {
    fun juice() = listOf(AppleJuice())
    fun mayBeJuice() = Optional.of(AppleJuice())
}

class MapVsFlatMap {
    val LOG = LoggerFactory.getLogger(MapVsFlatMap::class.java)

    @Test
    fun `should work`() {

        Mono.fromCallable {
            Thread.sleep(1000)
            "Rekha"
        }
            .map {
                it.toUpperCase()
            }
            .map {
                it.length
            }
            .log()
            .subscribe()


    }

    private fun toUppercase(it: String) = Mono.fromCallable {
        it.toUpperCase()
    }
}

// Optional<T>
// Either<L,R>

// List<T>

