package com.github.harshadnawathe.logger

import net.logstash.logback.marker.Markers
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager
import org.apache.logging.log4j.message.Message
import org.apache.logging.log4j.message.ObjectMessage
import org.junit.jupiter.api.Test

class LazyLoggerTest {

    companion object {
        @JvmStatic
        private val LOG : Logger = LogManager.getLogger(LazyLoggerTest::class.java)
    }


    @Test
    fun `some test`() {
        LOG.info{
           ObjectMessage(mapOf("foo" to "bar"))
        }
    }
}