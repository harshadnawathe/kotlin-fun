package com.github.harshadnawathe.reactive.retry

import com.somebank.lending.middleware.Client
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono


@SpringBootTest
@TestPropertySource(
    properties = [
        "middleware.client.retry.numRetries=4",
        "middleware.client.retry.firstBackOff=1s",
        "middleware.client.retry.maxBackOff=5s",
        "middleware.client.retry.jitterFactor=0.5"
    ]
)
class RetryTest {

    @Test
    fun shouldRetryIfResultIsNotValid() {

        Client().get()
            .log()
            .retryIf {
                println("Result : $it")
                it != 5
            }
            .block()

    }
}


@Configuration
class SpringConfing {

    @Bean
    fun bean1() : Map<String, Any> {
        return emptyMap()
    }

    @Bean
    fun bean2() : String {
        bean1()
        return "foo"
    }


}

@SpringBootApplication
class TestApplication
