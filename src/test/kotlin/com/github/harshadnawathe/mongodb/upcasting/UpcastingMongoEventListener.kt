package com.github.harshadnawathe.mongodb.upcasting

import org.bson.Document
import org.junit.platform.commons.util.AnnotationUtils
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent
import org.springframework.stereotype.Component

@Component
class UpcastingMongoEventListener(
    upcasters: List<Upcaster> = listOf()
) : AbstractMongoEventListener<Any>() {

    val upcasters = upcasters.groupBy { it.targetEntityClass }
        .mapValues { (k, v) ->
            UpcasterChain(k,v)
        }

    override fun onAfterLoad(event: AfterLoadEvent<Any>) {
        val document = event.document
        if(document != null) {
            upcasters[event.type]?.also {
                it.upcast(document)
            }
        }
    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(UpcastingMongoEventListener.javaClass)
    }
}

class UpcasterChain(
    val entity: Class<*>,
    upcasters: List<Upcaster>
) {
    private val upcasters = upcasters.sortedBy {
        it.sourceVersion
    }

    fun upcast(doc: Document) {
        val docSchemaVersion = schemaVersion(doc) ?: 0
        val entityVersion = schemaVersion(entity) ?: Int.MAX_VALUE
        upcasters.asSequence().filter {
            it.sourceVersion in docSchemaVersion until entityVersion
        }.forEach {
            LOG.info("Before Document: ${doc.toJson()}")
            it.upcast(doc)
            LOG.info("After Document: ${doc.toJson()}")
        }
    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(UpcasterChain::class.java)
    }
}

private fun schemaVersion(document: Document) : Int? {
    return document.getInteger("_schemaVersion")
}

private fun schemaVersion(entityClass: Class<*>) : Int? {
    val annotation = AnnotationUtils.findAnnotation(entityClass, SchemaVersion::class.java)
    return annotation.map { it.value }.orElse(null)
}