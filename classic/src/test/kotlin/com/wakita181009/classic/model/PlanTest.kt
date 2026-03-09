package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PlanTest {
    @Test
    fun `FREE tier plan with zero price is valid`() {
        assertDoesNotThrow {
            Plan(
                name = "Free",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("0.00"), Money.Currency.USD),
                tier = PlanTier.FREE,
                active = true,
                features = setOf("basic"),
            )
        }
    }

    @Test
    fun `FREE tier plan with non-zero price throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Plan(
                name = "Free",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("9.99"), Money.Currency.USD),
                tier = PlanTier.FREE,
                active = true,
                features = setOf("basic"),
            )
        }
    }

    @Test
    fun `non-FREE tier plan with positive price is valid`() {
        assertDoesNotThrow {
            Plan(
                name = "Starter",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("19.99"), Money.Currency.USD),
                tier = PlanTier.STARTER,
                active = true,
                features = setOf("basic", "email"),
            )
        }
    }

    @Test
    fun `non-FREE tier plan with zero price throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Plan(
                name = "Starter",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("0.00"), Money.Currency.USD),
                tier = PlanTier.STARTER,
                active = true,
                features = setOf("basic"),
            )
        }
    }
}
