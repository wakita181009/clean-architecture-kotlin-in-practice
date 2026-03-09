package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PlanTest : DescribeSpec({

    val planId = PlanId(1L).getOrNull()!!

    describe("Plan.of") {
        it("creates valid plan") {
            val plan = Plan.of(
                id = planId, name = "Pro", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
                usageLimit = 10000, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
            ).shouldBeRight()
            plan.name shouldBe "Pro"
        }

        it("creates free tier with zero price") {
            Plan.of(
                id = planId, name = "Free", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("0.00"), Currency.USD).getOrNull()!!,
                usageLimit = 100, features = setOf("basic"), tier = PlanTier.FREE, active = true,
            ).shouldBeRight()
        }

        it("rejects free tier with non-zero price") {
            Plan.of(
                id = planId, name = "Free", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!,
                usageLimit = 100, features = setOf("basic"), tier = PlanTier.FREE, active = true,
            ).shouldBeLeft()
        }

        it("rejects non-free tier with zero price") {
            Plan.of(
                id = planId, name = "Starter", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("0.00"), Currency.USD).getOrNull()!!,
                usageLimit = 100, features = setOf("basic"), tier = PlanTier.STARTER, active = true,
            ).shouldBeLeft()
        }

        it("rejects blank name") {
            Plan.of(
                id = planId, name = "", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
                usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
            ).shouldBeLeft()
        }

        it("rejects empty features") {
            Plan.of(
                id = planId, name = "Pro", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
                usageLimit = null, features = emptySet(), tier = PlanTier.PROFESSIONAL, active = true,
            ).shouldBeLeft()
        }
    }
})
