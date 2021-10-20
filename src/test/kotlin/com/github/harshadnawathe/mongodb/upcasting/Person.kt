package com.github.harshadnawathe.mongodb.upcasting

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

@SchemaVersion(1)
@Document(collection = "people")
class Person1 @PersistenceConstructor constructor(
    id: String?,
    val name: String,
    val age: Int
) {
    constructor(name: String, age: Int) : this(null, name, age)

    var id: String? = id
        private set
}

data class Name(val first: String, val middle: String? = null, val last: String)

@SchemaVersion(2)
@Document(collection = "people")
class Person2 @PersistenceConstructor constructor(
    id: String?,
    val name: Name,
    val age: Int
) {
    constructor(name: Name, age: Int) : this(null, name, age)

    var id: String? = id
        private set
}

data class Age(val age: Int)

@SchemaVersion(3)
@Document(collection = "people")
class Person @PersistenceConstructor constructor(
    id: String?,
    val name: Name,
    val age: Age
) {
    constructor(name: Name, age: Age) : this(null, name, age)

    var id: String? = id
        private set
}

@Repository
interface PersonRepository : ReactiveMongoRepository<Person, String>

@Component
class NameStringToObjectUpcaster : Upcaster {
    override val sourceVersion = 1
    override val targetEntityClass = Person::class.java

    private val mapper = jacksonObjectMapper()

    override fun upcast(source: org.bson.Document) {
        LOG.info("Converting name to object")
        val nameStr = source.getString("name")

        //This is naive - handle empty and singleton
        val (first, last) = nameStr.split(' ', limit = 2)

        val nameObj = Name(first = first, last = last)

        //This is naive too
        source["name"] = org.bson.Document.parse(mapper.writeValueAsString(nameObj))
    }

    companion object {
        @JvmStatic
        val LOG = LoggerFactory.getLogger(NameStringToObjectUpcaster::class.java)
    }
}

@Component
class AgeIntToObjectUpcaster : Upcaster {
    override val sourceVersion = 2
    override val targetEntityClass = Person::class.java

    override fun upcast(source: org.bson.Document) {
        LOG.info("Converting age to object")
        val ageInt = source.getInteger("age")

        source["age"] = org.bson.Document.parse("{\"age\": $ageInt }")
    }

    companion object {
        @JvmStatic
        val LOG = LoggerFactory.getLogger(NameStringToObjectUpcaster::class.java)
    }
}