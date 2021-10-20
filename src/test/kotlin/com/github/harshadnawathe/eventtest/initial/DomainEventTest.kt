package com.github.harshadnawathe.eventtest.initial

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.util.context.Context

//Application
@SpringBootApplication
class CounterApplication

//Aggregate

@Document(collection = "counter")
class Counter @PersistenceConstructor constructor(
    id: String?,
    @Indexed val name: String,
    count: Int = 0
) : AbstractAggregateRoot<Counter>() {
    @Id
    var id: String? = id
        private set

    var count: Int = count
        private set

    constructor(name: String, count: Int) : this(null, name, count) {
        registerEvent(
            CounterCreatedEvent(
                counter = this
            )
        )
    }

    fun increment() : Counter {
        val from = count++

        return this.andEvent(
            CounterIncrementedEvent(
                counter = this,
                changeDetails = CounterChangeDetails(from, count)
            )
        )
    }

    fun decrement() : Counter {
        if (count == 0) throw CounterException(id(), "[$name] Counter underflow")

        val from = count--

        return this.andEvent(
            CounterDecrementedEvent(
                counter = this,
                changeDetails = CounterChangeDetails(from, count)
            )
        )
    }

    fun current() = count

    private fun id() = id ?: "[Unknown]"
}

class CounterException(val id: String, msg: String) : RuntimeException(msg)

//Counter Events

abstract class CounterEvent(
    counter: Counter,
    val details: Any? = null
) : ApplicationEvent(counter) {
    open val type: String
        get() = this::class.java.simpleName
}

class CounterCreatedEvent(
    counter: Counter
) : CounterEvent(counter, details = CounterDetails(counter.count, counter.name)) {
    class CounterDetails(
        val count: Int,
        val name: String
    )
}

class CounterIncrementedEvent(
    counter: Counter,
    changeDetails: CounterChangeDetails
) : CounterEvent(counter, details = changeDetails)

class CounterDecrementedEvent(
    counter: Counter,
    changeDetails: CounterChangeDetails
) : CounterEvent(counter, details = changeDetails)

class CounterChangeDetails(
    val from: Int, val to: Int
) {
    override fun toString() = "CounterChangeDetails(from= $from, to= $to)"
}

//Repository

@Repository
interface CounterRepository : ReactiveMongoRepository<Counter, String>

//Service

class CounterNotFound(val id: String) :
    RuntimeException("Counter with id '$id' not found")

@Component
class CounterService(
    private val counterRepository: CounterRepository
) {
    private val log = LoggerFactory.getLogger(CounterService::class.java)

    fun create(name: String, initialCount: Int): Mono<Counter> {
        log.info("create")
        return counterRepository.save(
            Counter(name, initialCount)
        )
    }

    fun counterBy(id: String): Mono<Counter> {
        log.info("counterBy(id)")
        return counterRepository.findById(id)
            .switchIfEmpty(
                Mono.error(CounterNotFound(id))
            )
    }

    fun increment(id: String): Mono<Counter> {
        log.info("increment(id)")
        return counterRepository.findById(id)
            .doOnSuccess {
                log.info("Found counter, incrementing")
                it.increment()
            }
            .flatMap {
                log.info("Incremented counter, saving")
                counterRepository.save(it)
            }
            .switchIfEmpty(
                Mono.error(CounterNotFound(id))
            )
    }

    fun decrement(id: String): Mono<Counter> {
        log.info("decrement($id)")
        return counterRepository.findById(id)
            .doOnSuccess {
                log.info("Found counter, decrementing")
                it.decrement()
            }
            .flatMap {
                log.info("Decremented counter, saving")
                counterRepository.save(it)
            }
            .switchIfEmpty(
                Mono.error(CounterNotFound(id))
            )
    }
}

// Spring to Flux

class ApplicationListenerPublisher<T : ApplicationEvent> :
    ApplicationListener<T>,
    Publisher<T> {

    private val log = LoggerFactory.getLogger(ApplicationListenerPublisher::class.java)

    private val sink = Sinks.many().multicast().directBestEffort<T>()

    override fun onApplicationEvent(event: T) {
        log.info("Handling event " + event.javaClass.name)
        sink.tryEmitNext(event)
    }

    override fun subscribe(subscriber: Subscriber<in T>) {
        sink.asFlux().subscribe(subscriber)
    }
}

//Counter event publisher

@Configuration
class CounterEventListenerPublisherConfiguration {

    @Bean
    fun counterEventListenerPublisher() =
        ApplicationListenerPublisher<CounterEvent>()

}

// Web

class NewCounter(
    val name: String,
    val initialCount: Int = 0
)

@RestController
@RequestMapping(path = ["counter-service/v1/counter"])
class CounterController(
    private val service: CounterService
) {
    private val log = LoggerFactory.getLogger(CounterController::class.java)

    @PostMapping
    fun create(@RequestBody request: NewCounter): Mono<Counter> {
        log.info("POST counter-service/v1/counter")

        return service.create(request.name, request.initialCount).subscriberContext(
            Context.of("one", 1)
        )
    }

    @GetMapping(path = ["/{id}"])
    fun counterBy(@PathVariable id: String): Mono<Counter> {
        log.info("GET counter-service/v1/counter/$id")

        return service.counterBy(id)
    }

    @PostMapping(path = ["/{id}/increment"])
    fun increment(@PathVariable id: String): Mono<Counter> {
        log.info("POST counter-service/v1/counter/$id/increment")

        return service.increment(id)
    }

    @PostMapping(path = ["/{id}/decrement"])
    fun decrement(@PathVariable id: String): Mono<Counter> {
        log.info("POST counter-service/v1/counter/$id/decrement")

        return service.decrement(id)
    }

    @ExceptionHandler(CounterNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onError(e: CounterNotFound): Map<String, Any?> {
        return mapOf(
            "id" to e.id,
            "reason" to e.message
        )
    }

    @ExceptionHandler(CounterException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onError(e: CounterException): Map<String, Any?> {
        return mapOf(
            "id" to e.id,
            "reason" to e.message
        )
    }
}

//Reactive Event listener

abstract class ReactiveEventListener<T>(
    source: Publisher<T>
) {
    init {
        Flux.from(source).subscribe {
            this.handle(it)
        }
    }

    abstract fun handle(event: T)
}

// Event Handlers

@Component
class CounterEventHandler1(
    counterEventPublisher: Publisher<CounterEvent>
) : ReactiveEventListener<CounterEvent>(counterEventPublisher) {

    private val log = LoggerFactory.getLogger(CounterEventHandler1::class.java)

    override fun handle(event: CounterEvent) {
        log.info("==========> Received CounterEvent. ${event.type} ${event.details}")
    }
}

@Component
class CounterEventsKafkaPublisher(
    counterEventPublisher: Publisher<CounterEvent>
){
    private val log = LoggerFactory.getLogger(CounterEventsKafkaPublisher::class.java)

    init {
        //Can directly send the flux to Kafka using KafkaSender
        Flux.from(counterEventPublisher).subscribe {
            log.info("==========> Received CounterEvent. Publishing on Kafka . ${it.type} ${it.details}")
        }
    }
}


// ================== TEST ===============================================

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ExtendWith(SpringExtension::class)
class DomainEventsTest {

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `should work`() {

        val counter = client.post()
            .uri("/counter-service/v1/counter")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                BodyInserters.fromObject(
                    mapOf(
                        "name" to "my-counter",
                        "initialCount" to 5
                    )
                )
            )
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!

        val id = counter["id"] as String

        client.post()
            .uri("/counter-service/v1/counter/$id/increment")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody()
            .returnResult()
            .responseBodyContent
            .also {
                println(String(it!!))
            }

        client.post()
            .uri("/counter-service/v1/counter/$id/decrement")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody()
            .returnResult()
            .responseBodyContent
            .also {
                println(String(it!!))
            }

        client.post()
            .uri("/counter-service/v1/counter/$id/increment")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody()
            .returnResult()
            .responseBodyContent
            .also {
                println(String(it!!))
            }

        client.post()
            .uri("/counter-service/v1/counter/$id/decrement")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody()
            .returnResult()
            .responseBodyContent
            .also {
                println(String(it!!))
            }
    }
}