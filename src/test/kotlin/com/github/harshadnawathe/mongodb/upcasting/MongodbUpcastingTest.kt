package com.github.harshadnawathe.mongodb.upcasting

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate


@SpringBootTest
class MongodbUpcastingTest {

    @Autowired
    lateinit var counterRepository: CounterRepository

    @Autowired
    lateinit var personRepository: PersonRepository

    @Autowired
    lateinit var template: ReactiveMongoTemplate

    @Test
    fun `should upcast documents before conversion`() {
        LOG.info("upcast documents before conversion")

        val counter = template.save(OldCounter(4))
            .flatMap {
                counterRepository.findById(it.id!!)
            }
            .doOnSuccess {
                LOG.info("Counter: id=${it.id} count=${it.count} name=${it.name}")
            }
            .flatMap {
                it.count++
                counterRepository.save(it)
            }
            .block()!!

        counterRepository.findById(counter.id!!)
            .doOnSuccess {
                LOG.info("Counter: id=${it.id} count=${it.count} name=${it.name}")
            }.block()

        template.findById(counter.id!!, String::class.java, "counters")
            .doOnSuccess {
                LOG.info(it)
            }
            .block()
    }

    @Test
    fun `should upcast documents before conversion for Person 1 to 3`() {
        //Save old data format
        val bruce = template.save(Person1("Bruce Wayne", 35)).block()!!

        //Print
        template.findById(bruce.id!!, String::class.java, "people")
            .doOnSuccess {
                LOG.info("==========\n$it\n============")
            }
            .block()

        //Query new format
        personRepository.findById(bruce.id!!)
            .doOnSuccess {
                LOG.info("Person: id=${it.id} name=${it.name} age=${it.age}")
            }
            .flatMap {
                personRepository.save(it)
            }
            .block()!!

        //Print
        template.findById(bruce.id!!, String::class.java, "people")
            .doOnSuccess {
                LOG.info("==========\n$it\n============")
            }
            .block()
    }

    @Test
    fun `should upcast documents before conversion for Person 2 to 3`() {

        //Save old format
        val bruce = template.save(
            Person2(
                name = Name(first = "Bruce", middle = "Thomas", last = "Wayne"),
                age = 35
            )
        ).block()!!

        //Print
        template.findById(bruce.id!!, String::class.java, "people")
            .doOnSuccess {
                LOG.info("==========\n$it\n============")
            }
            .block()

        //Query new format and save
        personRepository.findById(bruce.id!!)
            .doOnSuccess {
                LOG.info("Person: id=${it.id} name=${it.name} age=${it.age}")
            }.flatMap {
                personRepository.save(it)
            }.block()!!

        //Print
        template.findById(bruce.id!!, String::class.java, "people")
            .doOnSuccess {
                LOG.info("==========\n$it\n============")
            }
            .block()
    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(MongodbUpcastingTest::class.java)
    }
}

@SpringBootApplication
class TestApplication
