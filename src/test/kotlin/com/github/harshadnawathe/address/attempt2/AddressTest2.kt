package com.github.harshadnawathe.address.attempt2



data class CustomerAddress(
    val line1: String,
    val line2: String,
    val line3: String,
    val line4: String,
    val line5: String
)





class Address(
    val line1: String,
    val line2: String,
    val line3: String,
    val line4: String,
    val line5: String
) {
    init {

    }


    constructor(a: CustomerAddress) : this(a.line1, a.line2, a.line3, a.line4, a.line5){
        val x = "hello"
    }


}




class AddressTest2 {



}