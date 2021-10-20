package com.github.harshadnawathe.dateTime.util

import java.util.*

fun <R> inTimeZone(zoneId: String, block: () -> R): R {
    val original: TimeZone = TimeZone.getDefault()
    try {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        return block()
    } finally {
        TimeZone.setDefault(original)
    }
}