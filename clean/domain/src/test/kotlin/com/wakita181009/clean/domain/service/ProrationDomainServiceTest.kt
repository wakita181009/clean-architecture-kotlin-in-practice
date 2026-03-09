package com.wakita181009.clean.domain.service

import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ProrationDomainServiceTest : DescribeSpec({

    val service = ProrationDomainService()

    fun plan(name: String, tier: PlanTier, priceStr: String, currency: Currency = Currency.USD): Plan {
        val price = Money.of(BigDecimal(priceStr), currency).getOrNull()!!
        return Plan.of(
            id = PlanId(1L).getOrNull()!!,
            name = name,
            billingInterval = BillingInterval.MONTHLY,
            basePrice = price,
            usageLimit = null,
            features = setOf("feature"),
            tier = tier,
            active = true,
        ).getOrNull()!!
    }

    describe("Proration calculation") {
        // CP-P1
        it("exact half-cycle: USD(49.99), 15 of 30 days") {
            val current = plan("Starter", PlanTier.STARTER, "49.99")
            val newPlan = plan("Pro", PlanTier.PROFESSIONAL, "99.99")
            val result = service.calculateProration(current, newPlan, 15, 30).shouldBeRight()
            result.credit.amount.amount shouldBe BigDecimal("-25.00") // 49.99 * 15/30 = 25.00, negated
            result.charge.amount.amount shouldBe BigDecimal("50.00") // 99.99 * 15/30 = 50.00
        }

        // CP-P2
        it("one-third cycle: USD(90.00), 10 of 30 days") {
            val current = plan("Current", PlanTier.STARTER, "90.00")
            val newPlan = plan("New", PlanTier.PROFESSIONAL, "120.00")
            val result = service.calculateProration(current, newPlan, 10, 30).shouldBeRight()
            result.credit.amount.amount shouldBe BigDecimal("-30.00") // 90.00 * 10/30 = 30.00, negated
        }

        // CP-P3
        it("rounding HALF_UP: USD(49.99), 17 of 30 days") {
            val current = plan("Current", PlanTier.STARTER, "49.99")
            val newPlan = plan("New", PlanTier.PROFESSIONAL, "99.99")
            val result = service.calculateProration(current, newPlan, 17, 30).shouldBeRight()
            result.credit.amount.amount shouldBe BigDecimal("-28.33") // 49.99 * 17/30 = 28.3277 -> 28.33
        }

        // CP-P4
        it("rounding boundary: USD(100.00), 1 of 3 days") {
            val current = plan("Current", PlanTier.STARTER, "100.00")
            val newPlan = plan("New", PlanTier.PROFESSIONAL, "200.00")
            val result = service.calculateProration(current, newPlan, 1, 3).shouldBeRight()
            result.credit.amount.amount shouldBe BigDecimal("-33.33") // 100.00 * 1/3 = 33.33
        }

        // CP-P5
        it("JPY rounding: JPY(4999), 17 of 30 days") {
            val current = plan("Current", PlanTier.STARTER, "4999", Currency.JPY)
            val newPlan = plan("New", PlanTier.PROFESSIONAL, "9999", Currency.JPY)
            val result = service.calculateProration(current, newPlan, 17, 30).shouldBeRight()
            result.credit.amount.amount shouldBe BigDecimal("-2833") // 4999 * 17/30 = 2832.77 -> 2833
        }

        it("net amount is charge minus credit") {
            val current = plan("Starter", PlanTier.STARTER, "19.99")
            val newPlan = plan("Pro", PlanTier.PROFESSIONAL, "49.99")
            val result = service.calculateProration(current, newPlan, 15, 30).shouldBeRight()
            result.netAmount.amount shouldBe BigDecimal("15.00") // 25.00 - 10.00 = 15.00
        }
    }
})
