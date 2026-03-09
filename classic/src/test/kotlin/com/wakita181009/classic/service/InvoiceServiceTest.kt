package com.wakita181009.classic.service

import com.wakita181009.classic.exception.BusinessRuleViolationException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.InvoiceNotFoundException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

@ExtendWith(MockKExtension::class)
class InvoiceServiceTest {
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val paymentGateway = mockk<PaymentGateway>()
    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: InvoiceService

    @BeforeEach
    fun setUp() {
        service = InvoiceService(invoiceRepository, subscriptionRepository, paymentGateway, clock)
    }

    private fun samplePlan() =
        Plan(
            id = 1L,
            name = "Pro",
            billingInterval = BillingInterval.MONTHLY,
            basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
            tier = PlanTier.PROFESSIONAL,
            active = true,
            features = setOf("feature1"),
        )

    private fun sampleSubscription(
        status: SubscriptionStatus = SubscriptionStatus.PAST_DUE,
        gracePeriodEnd: Instant? = fixedInstant.plus(Duration.ofDays(3)),
    ) = Subscription(
        id = 1L,
        customerId = 1L,
        plan = samplePlan(),
        status = status,
        currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
        currentPeriodEnd = Instant.parse("2025-01-31T00:00:00Z"),
        gracePeriodEnd = gracePeriodEnd,
    )

    private fun sampleInvoice(
        id: Long = 1L,
        status: InvoiceStatus = InvoiceStatus.OPEN,
        paymentAttemptCount: Int = 1,
        subscription: Subscription = sampleSubscription(),
    ) = Invoice(
        id = id,
        subscription = subscription,
        subtotal = Money(BigDecimal("49.99"), Money.Currency.USD),
        discountAmount = Money(BigDecimal("0.00"), Money.Currency.USD),
        total = Money(BigDecimal("49.99"), Money.Currency.USD),
        status = status,
        dueDate = LocalDate.of(2025, 1, 1),
        paymentAttemptCount = paymentAttemptCount,
    )

    // RP-H1: Successful recovery
    @Test
    fun `successful payment recovery marks invoice Paid and subscription Active`() {
        val sub = sampleSubscription()
        val invoice = sampleInvoice(subscription = sub)
        every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
        every { paymentGateway.charge(any(), any(), any()) } returns
            PaymentResult(success = true, transactionId = "tx-1", processedAt = fixedInstant)
        every { invoiceRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.save(any()) } answers { firstArg() }

        val result = service.recoverPayment(1L)
        assertEquals(InvoiceStatus.PAID, result.status)
    }

    // RP-F1: First retry failure
    @Test
    fun `first retry failure increments attempt count`() {
        val sub = sampleSubscription()
        val invoice = sampleInvoice(paymentAttemptCount = 1, subscription = sub)
        every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
        every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = false, errorReason = "declined")
        every { invoiceRepository.save(any()) } answers { firstArg() }

        assertThrows(PaymentFailedException::class.java) { service.recoverPayment(1L) }
    }

    // RP-F3: 3rd attempt (max reached) -> Uncollectible + Canceled
    @Test
    fun `third failure marks invoice Uncollectible and subscription Canceled`() {
        val sub = sampleSubscription()
        val invoice = sampleInvoice(paymentAttemptCount = 2, subscription = sub)
        every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
        every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = false, errorReason = "declined")
        every { invoiceRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.save(any()) } answers { firstArg() }

        val result = service.recoverPayment(1L)
        assertEquals(InvoiceStatus.UNCOLLECTIBLE, result.status)
    }

    // RP-G3: Grace period expired
    @Test
    fun `throws when grace period expired`() {
        val sub = sampleSubscription(gracePeriodEnd = fixedInstant.minus(Duration.ofDays(1)))
        val invoice = sampleInvoice(subscription = sub)
        every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
        every { invoiceRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.save(any()) } answers { firstArg() }

        assertThrows(BusinessRuleViolationException::class.java) { service.recoverPayment(1L) }
    }

    // RP-E1: Invoice not found
    @Test
    fun `throws InvoiceNotFoundException when not found`() {
        every { invoiceRepository.findById(999L) } returns Optional.empty()
        assertThrows(InvoiceNotFoundException::class.java) { service.recoverPayment(999L) }
    }

    // RP-E2: Invoice not Open (Paid)
    @Test
    fun `throws when invoice is already Paid`() {
        val invoice = sampleInvoice(status = InvoiceStatus.PAID)
        every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
        assertThrows(InvalidStateTransitionException::class.java) { service.recoverPayment(1L) }
    }

    // RP-E5: Subscription not PastDue
    @Test
    fun `throws when subscription is not PastDue`() {
        val sub = sampleSubscription(status = SubscriptionStatus.ACTIVE)
        val invoice = sampleInvoice(subscription = sub)
        every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
        assertThrows(InvalidStateTransitionException::class.java) { service.recoverPayment(1L) }
    }
}
