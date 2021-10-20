package com.github.harshadnawathe.logger

import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType
import reactor.util.context.Context


interface InfoLogger {
    fun info(msg: String)
}

interface DebugLogger {
    fun debug(msg: String)
}

interface WarnLogger {
    fun warn(msg: String)
}

interface ErrorLogger {
    fun error(msg: String)
}

interface SuccessLogger : InfoLogger, DebugLogger, WarnLogger

interface FailureLogger : ErrorLogger, WarnLogger


class Logger(val context: Context) : SuccessLogger, FailureLogger {

    override fun info(msg: String) {
        println("Info $msg")
    }

    override fun error(msg: String) {
        println("Error $msg")
    }

    override fun warn(msg: String) {
        println("Warn $msg")
    }

    override fun debug(msg: String) {
        println("Debug $msg")
    }
}

fun <T> Mono<T>.logOnSuccess(loggerFn: SuccessLogger.(T?) -> Unit) : Mono<T> {
    return this.doOnEach { signal ->
        if (signal.type == SignalType.ON_NEXT) {
            Logger(signal.context).loggerFn(signal.get())
        }
    }
}

fun <T> Mono<T>.logOnError(loggerFn: FailureLogger.(Throwable?) -> Unit) : Mono<T> {
    return this.doOnEach { signal ->
        if (signal.type == SignalType.ON_ERROR) {
            Logger(signal.context).loggerFn(signal.throwable)
        }
    }
}

fun functionAccessingContext() {
    Mono.subscriberContext().map { context ->


        if(context.isEmpty) {
            println("Context is empty")
            return@map 0
        }

        println("Context is available ${context}")
    }.map {
        42
    }.subscribe()
}



class LoggerTest {

    @Test
    fun `should log`() {

        val x = Mono.just(7)
            .logOnSuccess { data ->
                info("Info..$data")
            }
            .map { it * 2}
            .block()

        println(x)
    }

    @Test
    fun `should print context`() {

        Mono.fromCallable {
            Thread.sleep(1000)
            42
        }.logOnSuccess {
        }
            .doOnSuccess{
                functionAccessingContext()
            }
            .subscriberContext(Context.of("foo", "bar"))
            .block()
    }



}
