package com.github.harshadnawathe.auth.refresh

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.cache.CacheMono
import reactor.core.publisher.Mono
import reactor.core.publisher.Signal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Component
class AuthService {
    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(AuthService::class.java)
    }

    private val cache = ConcurrentHashMap<String, Signal<out String>>()

    fun token(id: String): Mono<String> = CacheMono.lookup(cache, id)
        .onCacheMissResume(serviceAccessToken())
        .also { LOG.debug("returned token with $id") }

    fun clearCache(id: String) {
        cache.remove(id).also {
            LOG.info("removed token with id $id from cache")
        }
    }

    private var count = 0

    private fun serviceAccessToken(): Mono<String> = Mono.fromSupplier {
        synchronized(count) {
            "service.access.token-${count++}".also {
                LOG.info("provided serviceAccessToken: $it")
            }
        }
    }
}