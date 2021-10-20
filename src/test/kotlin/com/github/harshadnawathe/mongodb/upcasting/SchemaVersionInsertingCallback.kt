package com.github.harshadnawathe.mongodb.upcasting

import org.bson.Document
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.mapping.event.ReactiveBeforeSaveCallback
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import kotlin.reflect.full.findAnnotation

@Component
class SchemaVersionInsertingCallback : ReactiveBeforeSaveCallback<Any> {

    override fun onBeforeSave(entity: Any, document: Document, collection: String): Publisher<Any> {
        return Mono.fromCallable {
            entity.also {
                schemaVersion(it)?.also { schemaVersion ->
                    document["_schemaVersion"] = schemaVersion
                }
            }
        }
    }

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(SchemaVersionInsertingCallback::class.java)
    }
}

private fun schemaVersion(entity: Any): Int? {
    return entity::class.findAnnotation<SchemaVersion>()?.value
}

