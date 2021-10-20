package com.github.harshadnawathe.mongodb.upcasting

import org.bson.Document

interface Upcaster {
    val sourceVersion: Int
    val targetEntityClass: Class<*>
    fun upcast(source: Document)
}