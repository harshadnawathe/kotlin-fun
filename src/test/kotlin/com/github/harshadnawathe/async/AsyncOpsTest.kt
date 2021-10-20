package com.github.harshadnawathe.async

import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import reactor.util.context.Context

//logOnSuccess uses context
fun <T> Mono<T>.logOnSuccess(event: String): Mono<T> {
    return this.doOnEach {
        if (it.type == SignalType.ON_NEXT) {
            val context = it.contextView
            if (context.isEmpty) {
                println("$event !!! [Context is empty] ${it.get()}")
            } else {
                val sessionId: String = context.get("session-id")
                println("$event Logging with session-id: '$sessionId' ${it.get()}")
            }
        }
    }
}

//----------------------------------------

inline fun <T> Mono<T>.doOnSuccessAsync(crossinline fn: (T) -> Mono<*>): Mono<T> {
    return doOnSuccessAsync(Schedulers.boundedElastic(), fn)
}


inline fun <T> Mono<T>.doOnSuccessAsync(
    scheduler: Scheduler,
    crossinline fn: (T) -> Mono<*>
): Mono<T> {
    return Mono.deferContextual { context ->
        this.doOnSuccess {
             fn(it)
                    .contextWrite(context)
                    .subscribeOn(scheduler)
                    .subscribe({}, { err ->
                        println(err) //This will print error
                    })
        }
    }
}

//----------------------------------------

data class Movie(val name: String)

data class Player(
    val token: String
)

fun updateRecommendations(userId: String): Mono<Boolean> {

    return Mono.fromCallable {
        println("updateRecommendations - started!")
        Thread.sleep(5000)
        true
    }.logOnSuccess("'UpdateRecommendations $userId'")
}


fun startPlaying1(userId: String, movie: Movie): Mono<Player> {
    return Mono.fromCallable {
        Player("Start-${movie.name}")
    }
        .logOnSuccess("PlayerCreated")
        .doOnSuccess {
            updateRecommendations(userId)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        }

}

fun startPlaying2(userId: String, movie: Movie): Mono<Player> {
    return Mono.fromCallable {
        Player("Start-${movie.name}")
    }
        .logOnSuccess("PlayerCreated")
        .doOnSuccessAsync {
            updateRecommendations(userId)
        }

}

//----------------------------------------

class AsyncOpsTest {

    @Test
    fun `should not log with context`() {
        startPlaying1("Peter Parker", Movie("Gunda"))
            .contextWrite(Context.of("session-id", "Session1"))
            .block()

        Thread.sleep(6000)
    }

    @Test
    fun `should log with context`() {
        startPlaying2("Peter Parker", Movie("Gunda"))
            .contextWrite(Context.of("session-id", "Session2"))
            .block()
        println("----------")

        Thread.sleep(6000)
        println("*********************")
    }
}

// ----------------
