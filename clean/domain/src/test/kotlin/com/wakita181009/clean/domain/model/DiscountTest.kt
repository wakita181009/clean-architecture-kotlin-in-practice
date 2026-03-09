package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant

class DiscountTest : DescribeSpec({

    val subId = SubscriptionId(1L).getOrNull()!!
    val now = Instant.parse("2025-01-15T00:00:00Z")

    describe("Discount.of validation") {
        // V-D1
        it("creates valid percentage discount") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).shouldBeRight()
        }

        // V-D2
        it("accepts percentage at minimum (1)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 1, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).shouldBeRight()
        }

        // V-D3
        it("accepts percentage at maximum (100)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 100, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).shouldBeRight()
        }

        // V-D4
        it("rejects percentage below minimum (0)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 0, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).shouldBeLeft()
        }

        // V-D5
        it("rejects percentage above maximum (101)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 101, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).shouldBeLeft()
        }

        // V-D6
        it("creates valid fixed amount discount") {
            val money = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.FIXED_AMOUNT,
                percentageValue = null, fixedAmountMoney = money,
                durationMonths = 6, remainingCycles = 6, appliedAt = now,
            ).shouldBeRight()
        }

        // V-D7
        it("rejects zero fixed amount") {
            val money = Money.of(BigDecimal("0.00"), Currency.USD).getOrNull()!!
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.FIXED_AMOUNT,
                percentageValue = null, fixedAmountMoney = money,
                durationMonths = 6, remainingCycles = 6, appliedAt = now,
            ).shouldBeLeft()
        }

        // V-D8
        it("accepts duration at minimum (1)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 1, remainingCycles = 1, appliedAt = now,
            ).shouldBeRight()
        }

        // V-D9
        it("accepts duration at maximum (24)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 24, remainingCycles = 24, appliedAt = now,
            ).shouldBeRight()
        }

        // V-D10
        it("accepts null duration (forever)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = null, remainingCycles = null, appliedAt = now,
            ).shouldBeRight()
        }

        // V-D11
        it("rejects duration above maximum (25)") {
            Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 25, remainingCycles = 25, appliedAt = now,
            ).shouldBeLeft()
        }
    }

    describe("Discount.apply") {
        it("applies percentage discount to subtotal") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).getOrNull()!!
            val subtotal = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!
            val result = discount.apply(subtotal)
            result.amount shouldBe BigDecimal("10.00") // 49.99 * 20/100 = 9.998 -> 10.00
        }

        // DI-4
        it("fixed discount capped at subtotal") {
            val money = Money.of(BigDecimal("100.00"), Currency.USD).getOrNull()!!
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.FIXED_AMOUNT,
                percentageValue = null, fixedAmountMoney = money,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).getOrNull()!!
            val subtotal = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!
            val result = discount.apply(subtotal)
            result.amount shouldBe BigDecimal("49.99") // capped at subtotal
        }
    }

    describe("Discount.decrementCycle") {
        it("decrements remaining cycles") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 3, appliedAt = now,
            ).getOrNull()!!
            val decremented = discount.decrementCycle()
            decremented.remainingCycles shouldBe 2
        }

        it("does not decrement below 0") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 1, remainingCycles = 0, appliedAt = now,
            ).getOrNull()!!
            val decremented = discount.decrementCycle()
            decremented.remainingCycles shouldBe 0
        }

        // DI-6 - forever discount
        it("does not change null remaining cycles") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = null, remainingCycles = null, appliedAt = now,
            ).getOrNull()!!
            val decremented = discount.decrementCycle()
            decremented.remainingCycles shouldBe null
        }
    }

    describe("Discount.isExpired") {
        it("expired when remaining is 0") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 1, remainingCycles = 0, appliedAt = now,
            ).getOrNull()!!
            discount.isExpired() shouldBe true
        }

        it("not expired when remaining is positive") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 2, appliedAt = now,
            ).getOrNull()!!
            discount.isExpired() shouldBe false
        }

        it("not expired when remaining is null (forever)") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = null, remainingCycles = null, appliedAt = now,
            ).getOrNull()!!
            discount.isExpired() shouldBe false
        }
    }
})
