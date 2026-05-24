package com.example.xpense

import com.example.xpense.data.model.Category
import com.example.xpense.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    @Test
    fun testDebitSmsParsing() {
        val sms = "Rs.500.00 debited from A/c **1234 on 01-Jan-24 to VPA Swiggy"
        val transaction = SmsParser.parseTransaction(sms)
        
        assertNotNull(transaction)
        assertEquals(500.0, transaction?.amount)
        assertEquals("Swiggy", transaction?.merchant)
        assertEquals(Category.FOOD, transaction?.category)
    }

    @Test
    fun testCreditSmsParsing() {
        val sms = "Your A/c X1234 has been credited by Rs 2000.0 on 05-Mar-24."
        val transaction = SmsParser.parseTransaction(sms)
        
        assertNull(transaction) // Should be null as we only track debits (expenses)
    }

    @Test
    fun testShoppingCategory() {
        val sms = "Rs 1500.0 spent at Amazon on 10-Feb-24."
        val transaction = SmsParser.parseTransaction(sms)
        
        assertNotNull(transaction)
        assertEquals(Category.SHOPPING, transaction?.category)
    }

    @Test
    fun testTransportCategory() {
        val sms = "INR 250.0 paid to Uber"
        val transaction = SmsParser.parseTransaction(sms)
        
        assertNotNull(transaction)
        assertEquals(Category.TRANSPORT, transaction?.category)
    }
}