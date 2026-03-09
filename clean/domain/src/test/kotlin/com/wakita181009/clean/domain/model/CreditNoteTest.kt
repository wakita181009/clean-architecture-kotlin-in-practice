package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant

class CreditNoteTest : DescribeSpec({

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val invoiceId = InvoiceId(1L).getOrNull()!!
    val subscriptionId = SubscriptionId(1L).getOrNull()!!

    describe("CreditNote.of") {
        // CN-V1
        it("creates valid full refund") {
            val result = CreditNote.of(
                id = null,
                invoiceId = invoiceId,
                subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
                reason = "Customer request",
                type = CreditNoteType.FULL,
                application = CreditNoteApplication.REFUND_TO_PAYMENT,
                status = CreditNoteStatus.Issued,
                refundTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ).shouldBeRight()
            result.type shouldBe CreditNoteType.FULL
            result.application shouldBe CreditNoteApplication.REFUND_TO_PAYMENT
        }

        // CN-V2
        it("creates valid partial refund") {
            CreditNote.of(
                id = null,
                invoiceId = invoiceId,
                subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("25.00"), Currency.USD).getOrNull()!!,
                reason = "Compensation",
                type = CreditNoteType.PARTIAL,
                application = CreditNoteApplication.ACCOUNT_CREDIT,
                status = CreditNoteStatus.Issued,
                refundTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ).shouldBeRight()
        }

        // CN-V3
        it("rejects partial with zero amount") {
            CreditNote.of(
                id = null,
                invoiceId = invoiceId,
                subscriptionId = subscriptionId,
                amount = Money.zero(Currency.USD),
                reason = "Test",
                type = CreditNoteType.PARTIAL,
                application = CreditNoteApplication.ACCOUNT_CREDIT,
                status = CreditNoteStatus.Issued,
                refundTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ).shouldBeLeft()
        }

        // CN-V4
        it("rejects partial with negative amount") {
            CreditNote.of(
                id = null,
                invoiceId = invoiceId,
                subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!.negate(),
                reason = "Test",
                type = CreditNoteType.PARTIAL,
                application = CreditNoteApplication.ACCOUNT_CREDIT,
                status = CreditNoteStatus.Issued,
                refundTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ).shouldBeLeft()
        }

        // CN-V5
        it("rejects blank reason") {
            CreditNote.of(
                id = null,
                invoiceId = invoiceId,
                subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
                reason = "",
                type = CreditNoteType.FULL,
                application = CreditNoteApplication.REFUND_TO_PAYMENT,
                status = CreditNoteStatus.Issued,
                refundTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ).shouldBeLeft()
        }

        // CN-V6
        it("creates valid JPY credit note") {
            CreditNote.of(
                id = null,
                invoiceId = invoiceId,
                subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("1000"), Currency.JPY).getOrNull()!!,
                reason = "Compensation",
                type = CreditNoteType.PARTIAL,
                application = CreditNoteApplication.ACCOUNT_CREDIT,
                status = CreditNoteStatus.Issued,
                refundTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ).shouldBeRight()
        }
    }

    describe("CreditNoteStatus state machine") {
        // CN-S1
        it("Issued transitions to Applied") {
            CreditNoteStatus.Issued.apply().shouldBeRight()
        }

        // CN-S3
        it("Issued transitions to Voided") {
            CreditNoteStatus.Issued.void().shouldBeRight()
        }

        // CN-I1, CN-I2: Applied and Voided are terminal — no transition methods available (compile-time enforcement)
    }

    describe("SubscriptionAddOnStatus state machine") {
        // SA-V1
        it("Active transitions to Detached") {
            val result = SubscriptionAddOnStatus.Active.detach(now).shouldBeRight()
            result.detachedAt shouldBe now
        }

        // SA-I1: Detached has no transition methods (compile-time enforcement)
    }
})
