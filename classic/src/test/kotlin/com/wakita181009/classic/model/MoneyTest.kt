package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {
    // V-M1: Valid USD
    @Test
    fun `valid USD money with scale 2`() {
        val money = Money(BigDecimal("49.99"), Money.Currency.USD)
        assertEquals(BigDecimal("49.99"), money.amount)
        assertEquals(Money.Currency.USD, money.currency)
        assertEquals(2, money.amount.scale())
    }

    // V-M2: Valid JPY
    @Test
    fun `valid JPY money with scale 0`() {
        val money = Money(BigDecimal("5000"), Money.Currency.JPY)
        assertEquals(BigDecimal("5000"), money.amount)
        assertEquals(Money.Currency.JPY, money.currency)
    }

    // V-M3: JPY rejects decimals
    @Test
    fun `JPY rejects decimal places`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(BigDecimal("99.99"), Money.Currency.JPY)
        }
    }

    // V-M4: Negative amount valid for credits
    @Test
    fun `negative amount is valid for credits`() {
        val money = Money(BigDecimal("-10.00"), Money.Currency.USD)
        assertEquals(BigDecimal("-10.00"), money.amount)
    }

    // V-M5: Addition same currency
    @Test
    fun `addition of same currency returns correct sum`() {
        val a = Money(BigDecimal("49.99"), Money.Currency.USD)
        val b = Money(BigDecimal("15.00"), Money.Currency.USD)
        val result = a + b
        assertEquals(BigDecimal("64.99"), result.amount)
        assertEquals(Money.Currency.USD, result.currency)
    }

    // V-M6: Addition cross-currency throws
    @Test
    fun `addition of different currencies throws`() {
        val usd = Money(BigDecimal("10.00"), Money.Currency.USD)
        val eur = Money(BigDecimal("5.00"), Money.Currency.EUR)
        assertThrows(IllegalArgumentException::class.java) { usd + eur }
    }

    // V-M7: Subtraction resulting in negative
    @Test
    fun `subtraction resulting in negative is valid for proration`() {
        val a = Money(BigDecimal("10.00"), Money.Currency.USD)
        val b = Money(BigDecimal("30.00"), Money.Currency.USD)
        val result = a - b
        assertEquals(BigDecimal("-20.00"), result.amount)
    }

    // V-M8: Multiplication by days ratio with HALF_UP rounding
    @Test
    fun `multiplication by ratio with HALF_UP rounding`() {
        val money = Money(BigDecimal("49.99"), Money.Currency.USD)
        val result = money.times(15L, 30L)
        assertEquals(BigDecimal("25.00"), result.amount)
    }

    // V-M9: Zero amount is valid
    @Test
    fun `zero amount is valid`() {
        val money = Money(BigDecimal("0.00"), Money.Currency.USD)
        assertEquals(BigDecimal("0.00"), money.amount)
    }

    // V-M10: HALF_UP rounding scenario
    @Test
    fun `HALF_UP rounding for USD proration`() {
        val money = Money(BigDecimal("49.99"), Money.Currency.USD)
        val result = money.times(17L, 30L)
        // 49.99 * 17 / 30 = 28.3277... -> 28.33 (HALF_UP)
        assertEquals(BigDecimal("28.33"), result.amount)
    }

    // V-M11: JPY rounding
    @Test
    fun `JPY rounding to integer`() {
        val money = Money(BigDecimal("4999"), Money.Currency.JPY)
        val result = money.times(17L, 30L)
        // 4999 * 17 / 30 = 2832.77 -> 2833 (HALF_UP)
        assertEquals(BigDecimal("2833"), result.amount)
    }

    // V-M12: Negate operation
    @Test
    fun `negate returns opposite sign`() {
        val money = Money(BigDecimal("49.99"), Money.Currency.USD)
        val result = money.negate()
        assertEquals(BigDecimal("-49.99"), result.amount)
        assertEquals(Money.Currency.USD, result.currency)
    }
}
