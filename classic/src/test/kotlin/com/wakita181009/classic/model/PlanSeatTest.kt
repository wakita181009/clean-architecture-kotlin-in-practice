package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PlanSeatTest {
    // PL-V1: Valid per-seat plan
    @Test
    fun `valid per-seat plan creates successfully`() {
        assertDoesNotThrow {
            Plan(
                name = "Team",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("10.00"), Money.Currency.USD),
                tier = PlanTier.PROFESSIONAL,
                features = setOf("feature1"),
                perSeatPricing = true,
                minimumSeats = 1,
                maximumSeats = 100,
            )
        }
    }

    // PL-V2: Per-seat with no max
    @Test
    fun `per-seat with no max creates successfully`() {
        assertDoesNotThrow {
            Plan(
                name = "Team",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("10.00"), Money.Currency.USD),
                tier = PlanTier.PROFESSIONAL,
                features = setOf("feature1"),
                perSeatPricing = true,
                minimumSeats = 1,
                maximumSeats = null,
            )
        }
    }

    // PL-V3: Per-seat min > max
    @Test
    fun `per-seat min greater than max throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Plan(
                name = "Team",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("10.00"), Money.Currency.USD),
                tier = PlanTier.PROFESSIONAL,
                features = setOf("feature1"),
                perSeatPricing = true,
                minimumSeats = 10,
                maximumSeats = 5,
            )
        }
    }

    // PL-V4: Per-seat min zero
    @Test
    fun `per-seat min zero throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Plan(
                name = "Team",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("10.00"), Money.Currency.USD),
                tier = PlanTier.PROFESSIONAL,
                features = setOf("feature1"),
                perSeatPricing = true,
                minimumSeats = 0,
                maximumSeats = 100,
            )
        }
    }

    // PL-V5: FREE tier per-seat
    @Test
    fun `FREE tier per-seat throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Plan(
                name = "Free",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("0.00"), Money.Currency.USD),
                tier = PlanTier.FREE,
                features = setOf("basic"),
                perSeatPricing = true,
            )
        }
    }

    // PL-V6: Non-per-seat ignores seats
    @Test
    fun `non-per-seat plan ignores seat fields`() {
        assertDoesNotThrow {
            Plan(
                name = "Starter",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("19.99"), Money.Currency.USD),
                tier = PlanTier.STARTER,
                features = setOf("feature1"),
                perSeatPricing = false,
                minimumSeats = 5,
                maximumSeats = 10,
            )
        }
    }
}
