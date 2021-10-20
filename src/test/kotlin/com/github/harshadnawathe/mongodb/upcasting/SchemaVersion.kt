package com.github.harshadnawathe.mongodb.upcasting

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaVersion(
    val value: Int = 0
)
