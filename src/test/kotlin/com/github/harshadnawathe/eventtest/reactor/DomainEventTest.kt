package com.github.harshadnawathe.eventtest.reactor

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
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
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.data.domain.DomainEvents
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
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

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

    fun increment(): Counter {
        val from = count++

        return this.andEvent(
            CounterIncrementedEvent(
                counter = this,
                changeDetails = CounterChangeDetails(from, count)
            )
        )
    }

    fun decrement(): Counter {
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


@Aspect
@Component
class DomainEventPublishingAspect(
    private val eventBus: DomainEventBus
) {

    @Pointcut(
        "this(org.springframework.data.mongodb.repository.ReactiveMongoRepository)"
    )
    fun repositoryBean() {
    }

    @Pointcut(
        "repositoryBean() " +
                "&& execution(public * save(..)) " +
                "&& args(entity)"
    )
    fun repositorySaveMethod(entity: Any) {
    }

    @Around("repositorySaveMethod(entity)")
    fun emitDomainEventsOnEventBus(joinPoint: ProceedingJoinPoint, entity: Any): Any {

        val events = entity::class.memberFunctions.firstOrNull {
            it.findAnnotation<DomainEvents>() != null
        }?.let {
            it.isAccessible = true
            it.call(entity) as List<*>
        }?.toList()

        val saveResultAsMono = joinPoint.proceed() as Mono<*>
        return saveResultAsMono.doOnEach {
            if(it.isOnNext) {
                events?.forEach { event ->
                    if (event != null)
                        eventBus.publish(event, it.context)
                }
            }
        }


//        return Mono.subscriberContext()
//            .flatMap {
//                joinPoint.proceed() as Mono<*>
//            }
//            .doOnError {
//                LOG.error("Error", it)
//            }
//            .doOnSuccess {
//                events?.forEach { event ->
//                    if(event != null)
//                        eventBus.publish(event)
//                }
//            }

    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(DomainEventPublishingAspect::class.java)
    }
}


@Component
class DomainEventBus : Publisher<Any> {

    private val flux = DirectProcessor.create<Any>()

    override fun subscribe(s: Subscriber<in Any>) {
        flux.subscribe(s)
    }

    fun publish(event: Any, context: Context){
        flux.onNext(event)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> filtered(): Flux<T> {
        return flux.filter {
            (it as? T) != null
        }.map {
            it as T
        }
    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(DomainEventBus::class.java)
    }
}

@Suppress("UNCHECKED_CAST")
abstract class ReactiveEventListener<T>(
    source: DomainEventBus
) {
    init {
        source.filtered<T>().subscribe { this.handle(it) }
    }

    abstract fun handle(event: T)

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(ReactiveEventListener::class.java)
    }

}

@Component
class CounterEventHandler(source: DomainEventBus) :
    ReactiveEventListener<CounterEvent>(source) {

    override fun handle(event: CounterEvent) {
        LOG.info("==========> Received CounterEvent. ${event.type} ${event.details}")
    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(CounterEventHandler::class.java)
    }
}

@Component
class CounterEventKafkaPublisher(source: DomainEventBus) {
    init {
        source.filtered<CounterEvent>()
            .flatMap { event ->
                Mono.subscriberContext().doOnSuccess {
                    LOG.info("Context empty: ${it.isEmpty}")
                    it.stream().forEach { (key, value) ->
                        LOG.info("Context $key to $value")
                    }
                }.map {
                    event
                }
            }
            .subscribe {
                LOG.info(">>>>>> Sending event to Kafka. ${it.type} ${it.details}")
            }
    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(CounterEventHandler::class.java)
    }
}


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

        return service.counterBy(id).subscriberContext {
            it.put("aKey", "aValue")
        }
    }

    @PostMapping(path = ["/{id}/increment"])
    fun increment(@PathVariable id: String): Mono<Counter> {
        log.info("POST counter-service/v1/counter/$id/increment")

        return service.increment(id).subscriberContext {
            it.put("aKey", "aValue")
        }
    }

    @PostMapping(path = ["/{id}/decrement"])
    fun decrement(@PathVariable id: String): Mono<Counter> {
        log.info("POST counter-service/v1/counter/$id/decrement")

        return service.decrement(id).subscriberContext {
            it.put("aKey", "aValue")
        }
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