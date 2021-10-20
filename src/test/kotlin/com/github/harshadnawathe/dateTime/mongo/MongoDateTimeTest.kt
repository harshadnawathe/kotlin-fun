package com.github.harshadnawathe.dateTime.mongo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant

@Document(collection = "records")
class Record(
    val timestamp: Instant,
    val data: String
) {
    @Id
    lateinit var id: String

    @PersistenceConstructor
    constructor(id: String, timestamp: Instant, data: String) : this(timestamp, data) {
        this.id = id
    }
}

@Repository
interface RecordRepository : MongoRepository<Record, String>


@SpringBootApplication
class TestApplication


@DataMongoTest
class MongoDateTimeTest {

    @Autowired
    lateinit var mongo: MongoOperations
    //    @Autowired
//    lateinit var repository: RecordRepository
//
    @Test
    fun `should store record with timestamp`() {
        val saved = mongo.save(Record(Instant.now(), "FooBar"))

        val str = mongo.findById(saved.id, String::class.java)

        println(str)
    }
}
