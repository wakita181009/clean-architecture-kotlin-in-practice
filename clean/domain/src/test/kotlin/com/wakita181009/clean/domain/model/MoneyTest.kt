package com.wakita181009.clean.domain.model

import com.wakita181009.clean.domain.error.MoneyError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class MoneyTest : DescribeSpec({

    describe("Money.of") {
        // V-M1
        it("creates valid USD money") {
            val money = Money.of(BigDecimal("49.99"), Currency.USD).shouldBeRight()
            money.amount shouldBe BigDecimal("49.99")
            money.currency shouldBe Currency.USD
        }

        // V-M2
        it("creates valid JPY money") {
            val money = Money.of(BigDecimal("5000"), Currency.JPY).shouldBeRight()
            money.amount shouldBe BigDecimal("5000")
            money.currency shouldBe Currency.JPY
        }

        // V-M3
        it("rejects JPY with decimal places") {
            Money.of(BigDecimal("99.99"), Currency.JPY).shouldBeLeft(MoneyError.InvalidJpyScale)
        }

        // V-M4
        it("allows negative amount (valid for credits)") {
            val money = Money.of(BigDecimal("-10.00"), Currency.USD).shouldBeRight()
            money.amount shouldBe BigDecimal("-10.00")
        }

        // V-M9
        it("allows zero amount") {
            val money = Money.of(BigDecimal("0.00"), Currency.USD).shouldBeRight()
            money.amount shouldBe BigDecimal("0.00")
        }
    }

    describe("Money arithmetic") {
        // V-M5
        it("adds same currency") {
            val a = Money.of(BigDecimal("49.99"), Currency.USD).shouldBeRight()
            val b = Money.of(BigDecimal("15.00"), Currency.USD).shouldBeRight()
            val result = (a + b).shouldBeRight()
            result.amount shouldBe BigDecimal("64.99")
        }

        // V-M6
        it("rejects addition of different currencies") {
            val a = Money.of(BigDecimal("10.00"), Currency.USD).shouldBeRight()
            val b = Money.of(BigDecimal("5.00"), Currency.EUR).shouldBeRight()
            (a + b).shouldBeLeft() shouldBe MoneyError.CurrencyMismatch(Currency.USD, Currency.EUR)
        }

        // V-M7
        it("allows subtraction resulting in negative") {
            val a = Money.of(BigDecimal("10.00"), Currency.USD).shouldBeRight()
            val b = Money.of(BigDecimal("30.00"), Currency.USD).shouldBeRight()
            val result = (a - b).shouldBeRight()
            result.amount shouldBe BigDecimal("-20.00")
        }

        // V-M8
        it("multiplies by days ratio with HALF_UP rounding") {
            val money = Money.of(BigDecimal("49.99"), Currency.USD).shouldBeRight()
            val result = money.multiply(15, 30)
            result.amount shouldBe BigDecimal("25.00")
        }

        // V-M10
        it("rounds HALF_UP: 49.99 * 17/30 = 28.33") {
            val money = Money.of(BigDecimal("49.99"), Currency.USD).shouldBeRight()
            val result = money.multiply(17, 30)
            result.amount shouldBe BigDecimal("28.33")
        }

        // V-M11
        it("rounds JPY to integer: 4999 * 17/30 = 2833") {
            val money = Money.of(BigDecimal("4999"), Currency.JPY).shouldBeRight()
            val result = money.multiply(17, 30)
            result.amount shouldBe BigDecimal("2833")
        }

        // V-M12
        it("negates money") {
            val money = Money.of(BigDecimal("49.99"), Currency.USD).shouldBeRight()
            money.negate().amount shouldBe BigDecimal("-49.99")
        }
    }
})
