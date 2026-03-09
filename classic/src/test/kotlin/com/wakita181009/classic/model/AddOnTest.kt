package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AddOnTest {
    // AO-V1: Valid FLAT add-on
    @Test
    fun `valid FLAT add-on creates successfully`() {
        val addOn =
            AddOn(
                name = "Priority Support",
                price = Money(BigDecimal("9.99"), Money.Currency.USD),
                billingType = BillingType.FLAT,
                compatibleTiers = setOf(PlanTier.STARTER, PlanTier.PROFESSIONAL),
                active = true,
            )
        assertEquals("Priority Support", addOn.name)
        assertEquals(BillingType.FLAT, addOn.billingType)
        assertEquals(Money.Currency.USD, addOn.currency)
    }

    // AO-V2: Valid PER_SEAT add-on
    @Test
    fun `valid PER_SEAT add-on creates successfully`() {
        assertDoesNotThrow {
            AddOn(
                name = "Extra Storage",
                price = Money(BigDecimal("2.00"), Money.Currency.USD),
                billingType = BillingType.PER_SEAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL, PlanTier.ENTERPRISE),
            )
        }
    }

    // AO-V3: Blank name
    @Test
    fun `blank name throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AddOn(
                name = "",
                price = Money(BigDecimal("9.99"), Money.Currency.USD),
                billingType = BillingType.FLAT,
                compatibleTiers = setOf(PlanTier.STARTER),
            )
        }
    }

    // AO-V4: Zero price
    @Test
    fun `zero price throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AddOn(
                name = "Test",
                price = Money(BigDecimal("0.00"), Money.Currency.USD),
                billingType = BillingType.FLAT,
                compatibleTiers = setOf(PlanTier.STARTER),
            )
        }
    }

    // AO-V5: Negative price
    @Test
    fun `negative price throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AddOn(
                name = "Test",
                price = Money(BigDecimal("-5.00"), Money.Currency.USD),
                billingType = BillingType.FLAT,
                compatibleTiers = setOf(PlanTier.STARTER),
            )
        }
    }

    // AO-V6: Empty compatible tiers
    @Test
    fun `empty compatible tiers throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AddOn(
                name = "Test",
                price = Money(BigDecimal("9.99"), Money.Currency.USD),
                billingType = BillingType.FLAT,
                compatibleTiers = emptySet(),
            )
        }
    }

    // AO-V7: Valid JPY add-on
    @Test
    fun `valid JPY add-on creates successfully`() {
        val addOn =
            AddOn(
                name = "JPY Add-on",
                price = Money(BigDecimal("500"), Money.Currency.JPY),
                billingType = BillingType.FLAT,
                compatibleTiers = setOf(PlanTier.STARTER),
            )
        assertEquals(Money.Currency.JPY, addOn.currency)
    }

    // AO-V8: JPY rejects decimals
    @Test
    fun `JPY with decimals throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AddOn(
                name = "JPY Add-on",
                price = Money(BigDecimal("9.99"), Money.Currency.JPY),
                billingType = BillingType.FLAT,
                compatibleTiers = setOf(PlanTier.STARTER),
            )
        }
    }
}
