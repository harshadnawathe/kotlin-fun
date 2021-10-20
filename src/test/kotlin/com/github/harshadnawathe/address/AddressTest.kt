package com.github.harshadnawathe.address

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.LoggerFactory

@JsonInclude(NON_NULL)
class Address(
    addressLineOne: String,
    addressLineTwo: String,
    addressLineThree: String,
    addressLineFour: String? = null,
    addressLineFive: String? = null,
    val city: String,
    val country: String,
    val state: String? = "",
    zipCode: Int? = null
) {

    private val logger = LoggerFactory.getLogger(Address::class.java)
    private val moderator = Moderator(119)

    init {
        moderator.push(addressLineOne, addressLineTwo, addressLineThree, addressLineFour, addressLineFive)
    }

    val addressLineOne = moderator.next()
    val addressLineTwo = moderator.next()
    val addressLineThree = moderator.next()
    val addressLineFour = moderator.nextOrNull()
    val addressLineFive = moderator.nextOrNull()

    init {
        if (moderator.overflow != null) {
            logger.warn("Address line overflow detected.")
        }
    }

    var zipCode: Int? = null
        private set

    init {
        initZipCode(zipCode)
    }

    @JsonSetter("zipCode")
    private fun initZipCode(value: Any?) {
        zipCode = when (value) {
            is Int -> ZipCode(value).value
            is String -> ZipCode(value).value
            else -> null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Address

        if (city != other.city) return false
        if (country != other.country) return false
        if (state != other.state) return false
        if (addressLineOne != other.addressLineOne) return false
        if (addressLineTwo != other.addressLineTwo) return false
        if (addressLineThree != other.addressLineThree) return false
        if (addressLineFour != other.addressLineFour) return false
        if (addressLineFive != other.addressLineFive) return false
        if (zipCode != other.zipCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = city.hashCode()
        result = 31 * result + country.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + addressLineOne.hashCode()
        result = 31 * result + addressLineTwo.hashCode()
        result = 31 * result + addressLineThree.hashCode()
        result = 31 * result + (addressLineFour?.hashCode() ?: 0)
        result = 31 * result + (addressLineFive?.hashCode() ?: 0)
        result = 31 * result + (zipCode ?: 0)
        return result
    }

    override fun toString(): String {
        return "Address(city='$city', country='$country', state=$state, addressLineOne='$addressLineOne', addressLineTwo='$addressLineTwo', addressLineThree='$addressLineThree', addressLineFour=$addressLineFour, addressLineFive=$addressLineFive, zipCode=$zipCode)"
    }
}

class ZipCode(value: Int?) {
    constructor(value: String) : this(value.toIntOrNull())

    val value: Int? = value.takeIf { IntRange(100000, 999999).contains(it) }
}

class Moderator(maxLength: Int) {
    private val buffer = LineBuffer(maxLength)
    val overflow: String?
        get() = buffer.overflow

    private lateinit var moderated: MutableList<String>
    private lateinit var iterator: Iterator<String>

    fun push(vararg lines: String?) {
        val moderated = lines.asSequence()
            .map { buffer + it }
            .filterNotNull()


        iterator = moderated.iterator()
    }

    fun hasNext() = iterator.hasNext()
    fun next() = iterator.next()
    fun nextOrNull() = if (iterator.hasNext()) iterator.next() else null
}

class LineBuffer(private val maxLength: Int) {
    init {
        require(maxLength >= 0) { "maxLength should not be negative" }
    }

    var overflow: String? = null
        private set

    private fun String.withPreviousOverflow() = when {
        overflow != null -> "$overflow $this"
        else -> this
    }

    operator fun plus(str: String): String {
        val strToFix = str.withPreviousOverflow()
        if (strToFix.length <= maxLength) {
            overflow = null
            return strToFix
        }

        overflow = strToFix.drop(maxLength)
        return strToFix.take(maxLength)
    }
}

operator fun LineBuffer.plus(str: String?) = when (str) {
    null -> overflow
    else -> plus(str)
}



class AddressTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `should serialize to correct json text`() {

        val address = Address(
            addressLineOne = "Address line one",
            addressLineTwo = "Address line two",
            addressLineThree = "Address line three",
            addressLineFour = "Address line four",
            addressLineFive = null,
            city = "City",
            state = "State",
            country = "Country",
            zipCode = 411022
        )

        val json = mapper.writeValueAsString(address)

        println(json)

        JSONAssert.assertEquals(
            """{
                  "addressLineOne" : "Address line one",
                  "addressLineTwo" : "Address line two",
                  "addressLineThree" : "Address line three",
                  "addressLineFour" : "Address line four",
                  "city": "City",
                  "country": "Country",
                  "state": "State",
                  "zipCode": 411022
                }""".trimIndent(),
            json,
            true
        )
    }

    @Test
    fun `should de-serialize from json`() {

        val json =
            "{\"addressLineOne\":\"Address line one\",\"addressLineTwo\":\"Address line two\",\"addressLineThree\":\"Address line three\",\"addressLineFour\":\"Address line four\",\"city\":\"City\",\"country\":\"Country\",\"state\":\"State\",\"zipCode\":\"411022\"}"

        val actual = mapper.readValue(json, Address::class.java)

        val expected = Address(
            addressLineOne = "Address line one",
            addressLineTwo = "Address line two",
            addressLineThree = "Address line three",
            addressLineFour = "Address line four",
            addressLineFive = null,
            city = "City",
            state = "State",
            country = "Country",
            zipCode = 411022
        )

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `overflow test`() {
        val a = Address(
            addressLineOne = RandomStringUtils.randomAlphabetic(120),
            addressLineTwo = RandomStringUtils.randomAlphabetic(120),
            addressLineThree = RandomStringUtils.randomAlphabetic(120),
            addressLineFour = RandomStringUtils.randomAlphabetic(120),
            addressLineFive = RandomStringUtils.randomAlphabetic(120),
            zipCode = 411022,
            city = "City",
            state = "State",
            country = "Country"
        )
        println(
            jacksonObjectMapper().writeValueAsString(a)
        )
    }

    @Test
    fun `buffer test`() {

        val buf = LineBuffer(10)

        buf.plus("Foo")

    }
}