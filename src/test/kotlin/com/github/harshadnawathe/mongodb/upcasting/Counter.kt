package com.github.harshadnawathe.mongodb.upcasting

import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

@SchemaVersion(1)
@Document(collection = "counters")
class OldCounter @PersistenceConstructor constructor(
    id: String?,
    count: Int
) {
    constructor(count: Int) : this(null, count)

    @Id
    var id: String? = id
        private set

    var count: Int = count
        private set
}

@SchemaVersion(2)
@Document(collection = "counters")
class Counter @PersistenceConstructor constructor(
    id: String?,
    val name: String,
    count: Int
) {
    constructor(name: String, count: Int = 0) : this(null, name, count)

    @Id
    var id: String? = id
        private set

    var count: Int = count
}

@Repository
interface CounterRepository : ReactiveMongoRepository<Counter, String>

@Component
class NameAddingUpcaster : Upcaster {
    override val sourceVersion = 1
    override val targetEntityClass = Counter::class.java

    override fun upcast(source: org.bson.Document) {
        LOG.info("Copying _id to name")
        source["name"] = source["_id"]
    }

    companion object {
        @JvmStatic
        val LOG = LoggerFactory.getLogger(NameAddingUpcaster::class.java)
    }
}