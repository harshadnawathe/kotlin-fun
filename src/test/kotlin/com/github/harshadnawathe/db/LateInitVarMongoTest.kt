package com.github.harshadnawathe.db

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.test.context.junit.jupiter.SpringExtension

@Document("account")
class Account {

    lateinit var recoveryEmail: String

    @Id
    lateinit var accountId: String

}

@SpringBootApplication
class TestApplication


@ExtendWith(SpringExtension::class)
@DataMongoTest
class LateInitVarMongoTest {

    @Autowired
    lateinit var mongo: MongoOperations

    @Test
    fun `should save Account in db`() {

        val acc = mongo.save(Account())

        println(acc.accountId)

        val acc2 = mongo.findById(acc.accountId, Account::class.java)!!

        acc2.recoveryEmail = "foo@bar.com"

        mongo.save(acc2)

        println(
            mongo.findById(acc.accountId, Map::class.java, "account")
        )
    }
}