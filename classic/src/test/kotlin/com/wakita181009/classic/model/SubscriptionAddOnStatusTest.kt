package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class SubscriptionAddOnStatusTest {
    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")

    private fun sampleSubscription() =
        Subscription(
            id = 1L,
            customerId = 1L,
            plan =
                Plan(
                    id = 1L,
                    name = "Pro",
                    billingInterval = BillingInterval.MONTHLY,
                    basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
                    tier = PlanTier.PROFESSIONAL,
                    features = setOf("f1"),
                ),
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = fixedInstant,
            currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
        )

    private fun sampleAddOn() =
        AddOn(
            id = 1L,
            name = "Support",
            price = Money(BigDecimal("9.99"), Money.Currency.USD),
            billingType = BillingType.FLAT,
            compatibleTiers = setOf(PlanTier.PROFESSIONAL),
        )

    // SA-V1: Active -> Detached (customer detaches)
    @Test
    fun `ACTIVE can transition to DETACHED`() {
        assertTrue(SubscriptionAddOnStatus.ACTIVE.canTransitionTo(SubscriptionAddOnStatus.DETACHED))
    }

    // SA-V2: Active -> Detached (plan change)
    @Test
    fun `transition sets status to DETACHED and detachedAt`() {
        val sa =
            SubscriptionAddOn(
                subscription = sampleSubscription(),
                addOn = sampleAddOn(),
                quantity = 1,
                status = SubscriptionAddOnStatus.ACTIVE,
                attachedAt = fixedInstant,
            )
        sa.transitionTo(SubscriptionAddOnStatus.DETACHED)
        sa.detachedAt = fixedInstant

        assertEquals(SubscriptionAddOnStatus.DETACHED, sa.status)
        assertEquals(fixedInstant, sa.detachedAt)
    }

    // SA-V3: Active -> Detached on subscription cancel
    @Test
    fun `ACTIVE transitions to DETACHED on cancellation`() {
        val sa =
            SubscriptionAddOn(
                subscription = sampleSubscription(),
                addOn = sampleAddOn(),
                quantity = 1,
                status = SubscriptionAddOnStatus.ACTIVE,
                attachedAt = fixedInstant,
            )
        sa.transitionTo(SubscriptionAddOnStatus.DETACHED)
        assertEquals(SubscriptionAddOnStatus.DETACHED, sa.status)
    }

    // SA-I1: Detached -> Active fails
    @Test
    fun `DETACHED cannot transition to ACTIVE`() {
        assertFalse(SubscriptionAddOnStatus.DETACHED.canTransitionTo(SubscriptionAddOnStatus.ACTIVE))
    }

    @Test
    fun `DETACHED transitionTo ACTIVE throws`() {
        val sa =
            SubscriptionAddOn(
                subscription = sampleSubscription(),
                addOn = sampleAddOn(),
                quantity = 1,
                status = SubscriptionAddOnStatus.ACTIVE,
                attachedAt = fixedInstant,
            )
        sa.transitionTo(SubscriptionAddOnStatus.DETACHED)
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            sa.transitionTo(SubscriptionAddOnStatus.ACTIVE)
        }
    }
}
