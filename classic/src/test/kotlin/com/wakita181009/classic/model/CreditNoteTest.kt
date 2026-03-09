package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class CreditNoteTest {
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
                    active = true,
                    features = setOf("feature1"),
                ),
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = fixedInstant,
            currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
        )

    private fun sampleInvoice(subscription: Subscription = sampleSubscription()) =
        Invoice(
            id = 1L,
            subscription = subscription,
            subtotal = Money(BigDecimal("49.99"), Money.Currency.USD),
            discountAmount = Money(BigDecimal("0.00"), Money.Currency.USD),
            total = Money(BigDecimal("49.99"), Money.Currency.USD),
            status = InvoiceStatus.PAID,
            dueDate = java.time.LocalDate.of(2025, 1, 1),
            paidAt = fixedInstant,
        )

    // CN-V1: Valid full refund
    @Test
    fun `valid full refund creates successfully`() {
        assertDoesNotThrow {
            CreditNote(
                invoice = sampleInvoice(),
                subscription = sampleSubscription(),
                amount = Money(BigDecimal("49.99"), Money.Currency.USD),
                reason = "Customer request",
                type = CreditNoteType.FULL,
                application = CreditApplication.REFUND_TO_PAYMENT,
            )
        }
    }

    // CN-V2: Valid partial refund
    @Test
    fun `valid partial refund creates successfully`() {
        assertDoesNotThrow {
            CreditNote(
                invoice = sampleInvoice(),
                subscription = sampleSubscription(),
                amount = Money(BigDecimal("25.00"), Money.Currency.USD),
                reason = "Compensation",
                type = CreditNoteType.PARTIAL,
                application = CreditApplication.ACCOUNT_CREDIT,
            )
        }
    }

    // CN-V3: Partial with zero amount
    @Test
    fun `partial with zero amount throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreditNote(
                invoice = sampleInvoice(),
                subscription = sampleSubscription(),
                amount = Money(BigDecimal("0.00"), Money.Currency.USD),
                reason = "Test",
                type = CreditNoteType.PARTIAL,
                application = CreditApplication.ACCOUNT_CREDIT,
            )
        }
    }

    // CN-V4: Partial with negative amount
    @Test
    fun `partial with negative amount throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreditNote(
                invoice = sampleInvoice(),
                subscription = sampleSubscription(),
                amount = Money(BigDecimal("-10.00"), Money.Currency.USD),
                reason = "Test",
                type = CreditNoteType.PARTIAL,
                application = CreditApplication.ACCOUNT_CREDIT,
            )
        }
    }

    // CN-V5: Blank reason
    @Test
    fun `blank reason throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreditNote(
                invoice = sampleInvoice(),
                subscription = sampleSubscription(),
                amount = Money(BigDecimal("25.00"), Money.Currency.USD),
                reason = "",
                type = CreditNoteType.FULL,
                application = CreditApplication.REFUND_TO_PAYMENT,
            )
        }
    }

    // CN-V6: Valid JPY credit note
    @Test
    fun `valid JPY credit note creates successfully`() {
        val jpyPlan =
            Plan(
                id = 2L,
                name = "JPY Pro",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("5000"), Money.Currency.JPY),
                tier = PlanTier.PROFESSIONAL,
                features = setOf("feature1"),
            )
        val jpySub =
            Subscription(
                id = 2L,
                customerId = 1L,
                plan = jpyPlan,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodStart = fixedInstant,
                currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
            )
        val jpyInvoice =
            Invoice(
                id = 2L,
                subscription = jpySub,
                subtotal = Money(BigDecimal("5000"), Money.Currency.JPY),
                discountAmount = Money(BigDecimal("0"), Money.Currency.JPY),
                total = Money(BigDecimal("5000"), Money.Currency.JPY),
                status = InvoiceStatus.PAID,
                dueDate = java.time.LocalDate.of(2025, 1, 1),
            )
        assertDoesNotThrow {
            CreditNote(
                invoice = jpyInvoice,
                subscription = jpySub,
                amount = Money(BigDecimal("1000"), Money.Currency.JPY),
                reason = "JPY refund",
                type = CreditNoteType.PARTIAL,
                application = CreditApplication.ACCOUNT_CREDIT,
            )
        }
    }
}
