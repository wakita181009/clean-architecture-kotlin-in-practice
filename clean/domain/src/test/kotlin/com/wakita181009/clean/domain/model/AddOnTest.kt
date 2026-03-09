package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class AddOnTest : DescribeSpec({

    val addOnId = AddOnId(1L).getOrNull()!!

    describe("AddOn.of") {
        // AO-V1
        it("creates valid FLAT add-on") {
            val result = AddOn.of(
                id = addOnId,
                name = "Priority Support",
                price = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = setOf(PlanTier.STARTER, PlanTier.PROFESSIONAL),
                active = true,
            ).shouldBeRight()
            result.name shouldBe "Priority Support"
            result.billingType shouldBe AddOnBillingType.FLAT
        }

        // AO-V2
        it("creates valid PER_SEAT add-on") {
            val result = AddOn.of(
                id = addOnId,
                name = "Extra Storage",
                price = Money.of(BigDecimal("2.00"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.PER_SEAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL, PlanTier.ENTERPRISE),
                active = true,
            ).shouldBeRight()
            result.billingType shouldBe AddOnBillingType.PER_SEAT
        }

        // AO-V3
        it("rejects blank name") {
            AddOn.of(
                id = addOnId,
                name = "",
                price = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                active = true,
            ).shouldBeLeft()
        }

        // AO-V4
        it("rejects zero price") {
            AddOn.of(
                id = addOnId,
                name = "Test",
                price = Money.of(BigDecimal("0.00"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                active = true,
            ).shouldBeLeft()
        }

        // AO-V5
        it("rejects negative price") {
            AddOn.of(
                id = addOnId,
                name = "Test",
                price = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!.negate(),
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                active = true,
            ).shouldBeLeft()
        }

        // AO-V6
        it("rejects empty compatible tiers") {
            AddOn.of(
                id = addOnId,
                name = "Test",
                price = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = emptySet(),
                active = true,
            ).shouldBeLeft()
        }

        // AO-V7
        it("creates valid JPY add-on") {
            val result = AddOn.of(
                id = addOnId,
                name = "Premium Feature",
                price = Money.of(BigDecimal("500"), Currency.JPY).getOrNull()!!,
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                active = true,
            ).shouldBeRight()
            result.price.amount.scale() shouldBe 0
        }

        // AO-V8
        it("rejects JPY with decimals") {
            Money.of(BigDecimal("9.99"), Currency.JPY).shouldBeLeft()
        }
    }
})
