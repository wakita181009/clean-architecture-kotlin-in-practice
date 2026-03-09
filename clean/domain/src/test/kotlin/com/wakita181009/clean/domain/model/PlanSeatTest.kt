package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PlanSeatTest : DescribeSpec({

    val planId = PlanId(1L).getOrNull()!!
    val usdPrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!

    fun basePlan(
        perSeatPricing: Boolean = false,
        minSeats: Int = 1,
        maxSeats: Int? = null,
        tier: PlanTier = PlanTier.PROFESSIONAL,
    ) = Plan.of(
        id = planId,
        name = "Test",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = if (tier == PlanTier.FREE) Money.zero(Currency.USD) else usdPrice,
        usageLimit = null,
        features = setOf("feature"),
        tier = tier,
        active = true,
        perSeatPricing = perSeatPricing,
        minSeats = minSeats,
        maxSeats = maxSeats,
    )

    describe("Plan per-seat pricing validation") {
        // PL-V1
        it("creates valid per-seat plan") {
            val plan = basePlan(perSeatPricing = true, minSeats = 1, maxSeats = 100).shouldBeRight()
            plan.perSeatPricing shouldBe true
            plan.minSeats shouldBe 1
            plan.maxSeats shouldBe 100
        }

        // PL-V2
        it("creates per-seat with no max (unlimited)") {
            val plan = basePlan(perSeatPricing = true, minSeats = 1, maxSeats = null).shouldBeRight()
            plan.maxSeats shouldBe null
        }

        // PL-V3
        it("rejects per-seat min > max") {
            basePlan(perSeatPricing = true, minSeats = 10, maxSeats = 5).shouldBeLeft()
        }

        // PL-V4
        it("rejects per-seat min zero") {
            basePlan(perSeatPricing = true, minSeats = 0).shouldBeLeft()
        }

        // PL-V5
        it("rejects FREE tier per-seat") {
            basePlan(perSeatPricing = true, tier = PlanTier.FREE).shouldBeLeft()
        }

        // PL-V6
        it("non-per-seat ignores seat fields") {
            val plan = basePlan(perSeatPricing = false, minSeats = 5, maxSeats = 10).shouldBeRight()
            plan.perSeatPricing shouldBe false
        }
    }

    describe("Value object ID tests") {
        it("AddOnId rejects non-positive") {
            AddOnId(0L).shouldBeLeft()
            AddOnId(-1L).shouldBeLeft()
        }

        it("AddOnId accepts positive") {
            AddOnId(1L).shouldBeRight()
        }

        it("SubscriptionAddOnId rejects non-positive") {
            SubscriptionAddOnId(0L).shouldBeLeft()
            SubscriptionAddOnId(-1L).shouldBeLeft()
        }

        it("SubscriptionAddOnId accepts positive") {
            SubscriptionAddOnId(1L).shouldBeRight()
        }

        it("CreditNoteId rejects non-positive") {
            CreditNoteId(0L).shouldBeLeft()
            CreditNoteId(-1L).shouldBeLeft()
        }

        it("CreditNoteId accepts positive") {
            CreditNoteId(1L).shouldBeRight()
        }
    }
})
