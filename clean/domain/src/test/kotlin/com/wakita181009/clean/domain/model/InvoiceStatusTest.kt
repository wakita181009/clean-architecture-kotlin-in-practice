package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

class InvoiceStatusTest : DescribeSpec({

    val now = Instant.parse("2025-01-15T00:00:00Z")

    describe("Valid transitions") {
        // IS-V1
        it("Draft -> Open") {
            InvoiceStatus.Draft.finalize().shouldBeRight()
                .shouldBeInstanceOf<InvoiceStatus.Open>()
        }

        // IS-V2
        it("Draft -> Void") {
            InvoiceStatus.Draft.void().shouldBeRight()
                .shouldBeInstanceOf<InvoiceStatus.Void>()
        }

        // IS-V3
        it("Open -> Paid") {
            val paid = InvoiceStatus.Open.pay(now).shouldBeRight()
            paid.shouldBeInstanceOf<InvoiceStatus.Paid>()
            paid.paidAt shouldBe now
        }

        // IS-V4
        it("Open -> Void") {
            InvoiceStatus.Open.void().shouldBeRight()
                .shouldBeInstanceOf<InvoiceStatus.Void>()
        }

        // IS-V5
        it("Open -> Uncollectible") {
            InvoiceStatus.Open.markUncollectible().shouldBeRight()
                .shouldBeInstanceOf<InvoiceStatus.Uncollectible>()
        }
    }

    describe("Terminal states") {
        // IS-I2: Paid is terminal
        it("Paid has no transition methods") {
            val paid = InvoiceStatus.Paid(now)
            paid.name shouldBe "PAID"
        }

        // IS-I3: Void is terminal
        it("Void has no transition methods") {
            InvoiceStatus.Void.name shouldBe "VOID"
        }

        // IS-I4: Uncollectible is terminal
        it("Uncollectible has no transition methods") {
            InvoiceStatus.Uncollectible.name shouldBe "UNCOLLECTIBLE"
        }
    }
})
