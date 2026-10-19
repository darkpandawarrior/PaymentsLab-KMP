package com.paymentslab.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    @Test
    fun groupsInrTheIndianWay() {
        assertEquals("₹149.00", formatMoney(14_900, "INR"))
        assertEquals("₹2,499.00", formatMoney(249_900, "INR"))
        assertEquals("₹9,999.00", formatMoney(999_900, "INR"))
        assertEquals("₹2,49,900.00", formatMoney(24_990_000, "INR"))
        assertEquals("₹1,00,00,000.00", formatMoney(1_000_000_000, "INR"))
    }

    @Test
    fun groupsOtherCurrenciesInThousands() {
        assertEquals("$9.00", formatMoney(900, "USD"))
        assertEquals("$1,234,567.89", formatMoney(123_456_789, "USD"))
        assertEquals("€1,000.00", formatMoney(100_000, "EUR"))
        assertEquals("£12.50", formatMoney(1_250, "GBP"))
        assertEquals("JPY 1,000.00", formatMoney(100_000, "JPY"))
    }

    @Test
    fun zeroAndNegative() {
        assertEquals("₹0.00", formatMoney(0, "INR"))
        assertEquals("-₹1,050.50", formatMoney(-105_050, "INR"))
    }
}
