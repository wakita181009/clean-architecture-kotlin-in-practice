package com.wakita181009.clean.domain.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BillingIntervalTest : DescribeSpec({

    fun instantOf(year: Int, month: Int, day: Int): Instant =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()

    describe("BillingInterval.nextPeriodEnd") {
        // V-BI1
        it("monthly: 2025-01-15 -> 2025-02-15") {
            val start = instantOf(2025, 1, 15)
            BillingInterval.MONTHLY.nextPeriodEnd(start) shouldBe instantOf(2025, 2, 15)
        }

        // V-BI2
        it("yearly: 2025-01-15 -> 2026-01-15") {
            val start = instantOf(2025, 1, 15)
            BillingInterval.YEARLY.nextPeriodEnd(start) shouldBe instantOf(2026, 1, 15)
        }

        // V-BI3
        it("monthly end-of-month: 2025-01-31 -> 2025-02-28") {
            val start = instantOf(2025, 1, 31)
            BillingInterval.MONTHLY.nextPeriodEnd(start) shouldBe instantOf(2025, 2, 28)
        }

        // V-BI4
        it("monthly leap year: 2024-01-31 -> 2024-02-29") {
            val start = instantOf(2024, 1, 31)
            BillingInterval.MONTHLY.nextPeriodEnd(start) shouldBe instantOf(2024, 2, 29)
        }

        // V-BI5
        it("yearly leap day: 2024-02-29 -> 2025-02-28") {
            val start = instantOf(2024, 2, 29)
            BillingInterval.YEARLY.nextPeriodEnd(start) shouldBe instantOf(2025, 2, 28)
        }
    }
})
