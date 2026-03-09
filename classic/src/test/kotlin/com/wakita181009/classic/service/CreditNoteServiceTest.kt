package com.wakita181009.classic.service

import com.wakita181009.classic.exception.AlreadyFullyRefundedException
import com.wakita181009.classic.exception.CreditAmountExceedsRemainingException
import com.wakita181009.classic.exception.InvoiceNotFoundException
import com.wakita181009.classic.exception.InvoiceNotPaidException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.CreditApplication
import com.wakita181009.classic.model.CreditNote
import com.wakita181009.classic.model.CreditNoteStatus
import com.wakita181009.classic.model.CreditNoteType
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.CreditNoteRepository
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
class CreditNoteServiceTest {
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val creditNoteRepository = mockk<CreditNoteRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val paymentGateway = mockk<PaymentGateway>()
    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: CreditNoteService

    @BeforeEach
    fun setUp() {
        service =
            CreditNoteService(
                invoiceRepository = invoiceRepository,
                creditNoteRepository = creditNoteRepository,
                subscriptionRepository = subscriptionRepository,
                paymentGateway = paymentGateway,
                clock = clock,
            )
    }

    private fun samplePlan() =
        Plan(
            id = 1L,
            name = "Pro",
            billingInterval = BillingInterval.MONTHLY,
            basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
            tier = PlanTier.PROFESSIONAL,
            features = setOf("feature1"),
        )

    private fun sampleSubscription(plan: Plan = samplePlan()) =
        Subscription(
            id = 1L,
            customerId = 1L,
            plan = plan,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = fixedInstant,
            currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
            accountCreditBalance = Money.zero(plan.basePrice.currency),
        )

    private fun sampleInvoice(
        id: Long = 1L,
        status: InvoiceStatus = InvoiceStatus.PAID,
        total: Money = Money(BigDecimal("49.99"), Money.Currency.USD),
        subscription: Subscription = sampleSubscription(),
    ) = Invoice(
        id = id,
        subscription = subscription,
        subtotal = total,
        discountAmount = Money(BigDecimal("0.00"), Money.Currency.USD),
        total = total,
        status = status,
        dueDate = LocalDate.of(2025, 1, 1),
        paidAt = fixedInstant,
    )

    @Nested
    inner class HappyPath {
        // CN-H1: Full refund to payment
        @Test
        fun `full refund to payment creates Applied credit note with refund tx id`() {
            val invoice = sampleInvoice()
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns emptyList()
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { paymentGateway.refund(any(), any()) } returns RefundResult(success = true, refundTransactionId = "ref-tx-1")

            val result =
                service.issueCreditNote(
                    invoiceId = 1L,
                    type = CreditNoteType.FULL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    amount = null,
                    reason = "Customer request",
                )

            assertEquals(CreditNoteStatus.APPLIED, result.status)
            assertNotNull(result.refundTransactionId)
            assertEquals(BigDecimal("49.99"), result.amount.amount)
        }

        // CN-H2: Full refund as account credit
        @Test
        fun `full refund as account credit adds to balance`() {
            val sub = sampleSubscription()
            val invoice = sampleInvoice(subscription = sub)
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns emptyList()
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result =
                service.issueCreditNote(
                    invoiceId = 1L,
                    type = CreditNoteType.FULL,
                    application = CreditApplication.ACCOUNT_CREDIT,
                    amount = null,
                    reason = "Customer request",
                )

            assertEquals(CreditNoteStatus.APPLIED, result.status)
            // Account balance should be updated
            verify { subscriptionRepository.save(match { it.accountCreditBalance.amount == BigDecimal("49.99") }) }
        }

        // CN-H3: Partial refund to payment
        @Test
        fun `partial refund to payment creates Applied credit note`() {
            val invoice = sampleInvoice()
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns emptyList()
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { paymentGateway.refund(any(), any()) } returns RefundResult(success = true, refundTransactionId = "ref-tx-2")

            val result =
                service.issueCreditNote(
                    invoiceId = 1L,
                    type = CreditNoteType.PARTIAL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    amount = BigDecimal("20.00"),
                    reason = "Partial refund",
                )

            assertEquals(CreditNoteStatus.APPLIED, result.status)
            assertEquals(BigDecimal("20.00"), result.amount.amount)
        }

        // CN-H4: Partial refund as credit
        @Test
        fun `partial refund as account credit adds to balance`() {
            val sub = sampleSubscription()
            val invoice = sampleInvoice(subscription = sub)
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns emptyList()
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result =
                service.issueCreditNote(
                    invoiceId = 1L,
                    type = CreditNoteType.PARTIAL,
                    application = CreditApplication.ACCOUNT_CREDIT,
                    amount = BigDecimal("10.00"),
                    reason = "Compensation",
                )

            assertEquals(CreditNoteStatus.APPLIED, result.status)
            assertEquals(BigDecimal("10.00"), result.amount.amount)
        }

        // CN-H5: Second partial (remaining)
        @Test
        fun `second partial refund for remaining amount succeeds`() {
            val invoice = sampleInvoice()
            val existingCredit =
                CreditNote(
                    id = 1L,
                    invoice = invoice,
                    subscription = invoice.subscription,
                    amount = Money(BigDecimal("20.00"), Money.Currency.USD),
                    reason = "First partial",
                    type = CreditNoteType.PARTIAL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    status = CreditNoteStatus.APPLIED,
                )

            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns listOf(existingCredit)
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { paymentGateway.refund(any(), any()) } returns RefundResult(success = true, refundTransactionId = "ref-tx-3")

            val result =
                service.issueCreditNote(
                    invoiceId = 1L,
                    type = CreditNoteType.PARTIAL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    amount = BigDecimal("29.99"),
                    reason = "Second partial",
                )

            assertEquals(CreditNoteStatus.APPLIED, result.status)
            assertEquals(BigDecimal("29.99"), result.amount.amount)
        }

        // CN-H6: Full after partial
        @Test
        fun `full refund after partial uses remaining amount`() {
            val invoice = sampleInvoice()
            val existingCredit =
                CreditNote(
                    id = 1L,
                    invoice = invoice,
                    subscription = invoice.subscription,
                    amount = Money(BigDecimal("20.00"), Money.Currency.USD),
                    reason = "First partial",
                    type = CreditNoteType.PARTIAL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    status = CreditNoteStatus.APPLIED,
                )

            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns listOf(existingCredit)
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { paymentGateway.refund(any(), any()) } returns RefundResult(success = true, refundTransactionId = "ref-tx-4")

            val result =
                service.issueCreditNote(
                    invoiceId = 1L,
                    type = CreditNoteType.FULL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    amount = null,
                    reason = "Full remaining",
                )

            assertEquals(CreditNoteStatus.APPLIED, result.status)
            assertEquals(BigDecimal("29.99"), result.amount.amount)
        }

        // CN-H7: JPY refund
        @Test
        fun `JPY refund creates credit note with correct amount`() {
            val jpyPlan =
                Plan(
                    id = 2L,
                    name = "JPY Pro",
                    billingInterval = BillingInterval.MONTHLY,
                    basePrice = Money(BigDecimal("5000"), Money.Currency.JPY),
                    tier = PlanTier.PROFESSIONAL,
                    features = setOf("feature1"),
                )
            val jpySub = sampleSubscription(plan = jpyPlan)
            val jpyInvoice =
                sampleInvoice(
                    id = 2L,
                    total = Money(BigDecimal("5000"), Money.Currency.JPY),
                    subscription = jpySub,
                )

            every { invoiceRepository.findById(2L) } returns Optional.of(jpyInvoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(2L, any()) } returns emptyList()
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result =
                service.issueCreditNote(
                    invoiceId = 2L,
                    type = CreditNoteType.FULL,
                    application = CreditApplication.ACCOUNT_CREDIT,
                    amount = null,
                    reason = "JPY refund",
                )

            assertEquals(CreditNoteStatus.APPLIED, result.status)
            assertEquals(BigDecimal("5000"), result.amount.amount)
        }
    }

    @Nested
    inner class Errors {
        // CN-E1: Invoice not found
        @Test
        fun `throws InvoiceNotFoundException when invoice not found`() {
            every { invoiceRepository.findById(999L) } returns Optional.empty()
            assertThrows(InvoiceNotFoundException::class.java) {
                service.issueCreditNote(999L, CreditNoteType.FULL, CreditApplication.REFUND_TO_PAYMENT, null, "test")
            }
        }

        // CN-E2: Invoice not Paid (Open)
        @Test
        fun `throws InvoiceNotPaidException when invoice is Open`() {
            val invoice = sampleInvoice(status = InvoiceStatus.OPEN)
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            assertThrows(InvoiceNotPaidException::class.java) {
                service.issueCreditNote(1L, CreditNoteType.FULL, CreditApplication.REFUND_TO_PAYMENT, null, "test")
            }
        }

        // CN-E3: Invoice not Paid (Void)
        @Test
        fun `throws InvoiceNotPaidException when invoice is Void`() {
            val invoice = sampleInvoice(status = InvoiceStatus.VOID)
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            assertThrows(InvoiceNotPaidException::class.java) {
                service.issueCreditNote(1L, CreditNoteType.FULL, CreditApplication.REFUND_TO_PAYMENT, null, "test")
            }
        }

        // CN-E4: Invoice not Paid (Uncollectible)
        @Test
        fun `throws InvoiceNotPaidException when invoice is Uncollectible`() {
            val invoice = sampleInvoice(status = InvoiceStatus.UNCOLLECTIBLE)
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            assertThrows(InvoiceNotPaidException::class.java) {
                service.issueCreditNote(1L, CreditNoteType.FULL, CreditApplication.REFUND_TO_PAYMENT, null, "test")
            }
        }

        // CN-E5: Already fully refunded
        @Test
        fun `throws AlreadyFullyRefundedException when fully refunded`() {
            val invoice = sampleInvoice()
            val existingCredit =
                CreditNote(
                    id = 1L,
                    invoice = invoice,
                    subscription = invoice.subscription,
                    amount = Money(BigDecimal("49.99"), Money.Currency.USD),
                    reason = "Full refund",
                    type = CreditNoteType.FULL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    status = CreditNoteStatus.APPLIED,
                )

            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns listOf(existingCredit)

            assertThrows(AlreadyFullyRefundedException::class.java) {
                service.issueCreditNote(1L, CreditNoteType.FULL, CreditApplication.REFUND_TO_PAYMENT, null, "test")
            }
        }

        // CN-E6: Partial exceeds remaining
        @Test
        fun `throws CreditAmountExceedsRemainingException when partial exceeds remaining`() {
            val invoice = sampleInvoice()
            val existingCredit =
                CreditNote(
                    id = 1L,
                    invoice = invoice,
                    subscription = invoice.subscription,
                    amount = Money(BigDecimal("40.00"), Money.Currency.USD),
                    reason = "First partial",
                    type = CreditNoteType.PARTIAL,
                    application = CreditApplication.REFUND_TO_PAYMENT,
                    status = CreditNoteStatus.APPLIED,
                )

            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns listOf(existingCredit)

            assertThrows(CreditAmountExceedsRemainingException::class.java) {
                service.issueCreditNote(1L, CreditNoteType.PARTIAL, CreditApplication.REFUND_TO_PAYMENT, BigDecimal("10.00"), "test")
            }
        }

        // CN-E10: Gateway refund fails
        @Test
        fun `throws PaymentFailedException when gateway refund fails and credit note stays Issued`() {
            val invoice = sampleInvoice()
            every { invoiceRepository.findById(1L) } returns Optional.of(invoice)
            every { creditNoteRepository.findByInvoiceIdAndStatusIn(1L, any()) } returns emptyList()
            every { creditNoteRepository.save(any()) } answers { firstArg() }
            every { paymentGateway.refund(any(), any()) } returns RefundResult(success = false, errorReason = "Gateway error")

            assertThrows(PaymentFailedException::class.java) {
                service.issueCreditNote(1L, CreditNoteType.FULL, CreditApplication.REFUND_TO_PAYMENT, null, "test")
            }
        }
    }
}
