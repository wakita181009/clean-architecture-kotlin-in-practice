package com.wakita181009.clean.application.command.usecase

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.dto.PaymentError
import com.wakita181009.clean.application.command.dto.RefundResult
import com.wakita181009.clean.application.command.error.IssueCreditNoteError
import com.wakita181009.clean.application.command.port.CreditNoteCommandQueryPort
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.CreditNote
import com.wakita181009.clean.domain.model.CreditNoteApplication
import com.wakita181009.clean.domain.model.CreditNoteId
import com.wakita181009.clean.domain.model.CreditNoteStatus
import com.wakita181009.clean.domain.model.CreditNoteType
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.PaymentMethod
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.CreditNoteRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class IssueCreditNoteUseCaseTest : DescribeSpec({

    val invoiceCommandQueryPort = mockk<InvoiceCommandQueryPort>()
    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val creditNoteCommandQueryPort = mockk<CreditNoteCommandQueryPort>()
    val creditNoteRepository = mockk<CreditNoteRepository>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val paymentGatewayPort = mockk<PaymentGatewayPort>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = IssueCreditNoteUseCaseImpl(
        invoiceCommandQueryPort = invoiceCommandQueryPort,
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        creditNoteCommandQueryPort = creditNoteCommandQueryPort,
        creditNoteRepository = creditNoteRepository,
        subscriptionRepository = subscriptionRepository,
        paymentGatewayPort = paymentGatewayPort,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val invoiceId = InvoiceId(1L).getOrNull()!!
    val subscriptionId = SubscriptionId(1L).getOrNull()!!
    val usd = Currency.USD

    val plan = Plan.of(
        id = PlanId(1L).getOrNull()!!, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
    ).getOrNull()!!

    fun paidInvoice(total: BigDecimal = BigDecimal("49.99")) = Invoice(
        id = invoiceId, subscriptionId = subscriptionId, lineItems = emptyList(),
        subtotal = Money.of(total, usd).getOrNull()!!, discountAmount = Money.zero(usd),
        total = Money.of(total, usd).getOrNull()!!, currency = usd,
        status = InvoiceStatus.Paid(now), dueDate = LocalDate.of(2025, 1, 15),
        paidAt = now, paymentAttemptCount = 1, createdAt = now, updatedAt = now,
    )

    fun subscription() = Subscription(
        id = subscriptionId, customerId = CustomerId(1L).getOrNull()!!, plan = plan,
        status = SubscriptionStatus.Active,
        currentPeriodStart = now, currentPeriodEnd = now.plusSeconds(86400 * 30),
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD, createdAt = now, updatedAt = now,
    )

    beforeTest {
        clearMocks(
            invoiceCommandQueryPort, subscriptionCommandQueryPort, creditNoteCommandQueryPort,
            creditNoteRepository, subscriptionRepository, paymentGatewayPort, clockPort, transactionPort,
        )
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
    }

    describe("Happy path") {
        // CN-H1
        it("full refund to payment") {
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns emptyList<CreditNote>().right()
            every { paymentGatewayPort.refund(any(), any()) } returns RefundResult("ref_1", now).right()
            every { creditNoteRepository.save(any()) } answers {
                firstArg<CreditNote>().copy(id = CreditNoteId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Customer request").shouldBeRight()
            result.status shouldBe CreditNoteStatus.Applied
            result.refundTransactionId shouldBe "ref_1"
        }

        // CN-H2
        it("full refund as account credit") {
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns emptyList<CreditNote>().right()
            every { subscriptionCommandQueryPort.findById(subscriptionId) } returns subscription().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            every { creditNoteRepository.save(any()) } answers {
                firstArg<CreditNote>().copy(id = CreditNoteId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(1L, "FULL", "ACCOUNT_CREDIT", null, "Compensation").shouldBeRight()
            result.status shouldBe CreditNoteStatus.Applied
        }

        // CN-H3
        it("partial refund to payment") {
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns emptyList<CreditNote>().right()
            every { paymentGatewayPort.refund(any(), any()) } returns RefundResult("ref_2", now).right()
            every { creditNoteRepository.save(any()) } answers {
                firstArg<CreditNote>().copy(id = CreditNoteId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(1L, "PARTIAL", "REFUND_TO_PAYMENT", BigDecimal("20.00"), "Partial refund").shouldBeRight()
            result.amount.amount shouldBe BigDecimal("20.00")
        }

        // CN-H4
        it("partial refund as credit") {
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns emptyList<CreditNote>().right()
            every { subscriptionCommandQueryPort.findById(subscriptionId) } returns subscription().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            every { creditNoteRepository.save(any()) } answers {
                firstArg<CreditNote>().copy(id = CreditNoteId(1L).getOrNull()!!).right()
            }

            useCase.execute(1L, "PARTIAL", "ACCOUNT_CREDIT", BigDecimal("10.00"), "Compensation").shouldBeRight()
        }

        // CN-H5
        it("second partial after existing credit") {
            val existingCredit = CreditNote(
                id = CreditNoteId(1L).getOrNull()!!, invoiceId = invoiceId, subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("20.00"), usd).getOrNull()!!, reason = "First",
                type = CreditNoteType.PARTIAL, application = CreditNoteApplication.REFUND_TO_PAYMENT,
                status = CreditNoteStatus.Applied, refundTransactionId = "ref_1",
                createdAt = now, updatedAt = now,
            )
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns listOf(existingCredit).right()
            every { paymentGatewayPort.refund(any(), any()) } returns RefundResult("ref_2", now).right()
            every { creditNoteRepository.save(any()) } answers {
                firstArg<CreditNote>().copy(id = CreditNoteId(2L).getOrNull()!!).right()
            }

            val result = useCase.execute(1L, "PARTIAL", "REFUND_TO_PAYMENT", BigDecimal("29.99"), "Remaining").shouldBeRight()
            result.amount.amount shouldBe BigDecimal("29.99")
        }

        // CN-H6
        it("full after partial takes remaining") {
            val existingCredit = CreditNote(
                id = CreditNoteId(1L).getOrNull()!!, invoiceId = invoiceId, subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("20.00"), usd).getOrNull()!!, reason = "Partial",
                type = CreditNoteType.PARTIAL, application = CreditNoteApplication.REFUND_TO_PAYMENT,
                status = CreditNoteStatus.Applied, refundTransactionId = "ref_1",
                createdAt = now, updatedAt = now,
            )
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns listOf(existingCredit).right()
            every { paymentGatewayPort.refund(any(), any()) } returns RefundResult("ref_2", now).right()
            every { creditNoteRepository.save(any()) } answers {
                firstArg<CreditNote>().copy(id = CreditNoteId(2L).getOrNull()!!).right()
            }

            val result = useCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Full remaining").shouldBeRight()
            result.amount.amount shouldBe BigDecimal("29.99")
        }
    }

    describe("Validation errors") {
        // CN-E1
        it("returns error when invoice not found") {
            every { invoiceCommandQueryPort.findById(invoiceId) } returns (object : DomainError { override val message = "not found" }).left()
            useCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.InvoiceNotFound>()
        }

        // CN-E2
        it("returns error when invoice is Open") {
            val openInvoice = paidInvoice().copy(status = InvoiceStatus.Open)
            every { invoiceCommandQueryPort.findById(invoiceId) } returns openInvoice.right()
            useCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.InvoiceNotPaid>()
        }

        // CN-E5
        it("returns error when already fully refunded") {
            val existingCredit = CreditNote(
                id = CreditNoteId(1L).getOrNull()!!, invoiceId = invoiceId, subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("49.99"), usd).getOrNull()!!, reason = "Full",
                type = CreditNoteType.FULL, application = CreditNoteApplication.REFUND_TO_PAYMENT,
                status = CreditNoteStatus.Applied, refundTransactionId = "ref_1",
                createdAt = now, updatedAt = now,
            )
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns listOf(existingCredit).right()

            useCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.AlreadyFullyRefunded>()
        }

        // CN-E6
        it("returns error when partial exceeds remaining") {
            val existingCredit = CreditNote(
                id = CreditNoteId(1L).getOrNull()!!, invoiceId = invoiceId, subscriptionId = subscriptionId,
                amount = Money.of(BigDecimal("40.00"), usd).getOrNull()!!, reason = "Partial",
                type = CreditNoteType.PARTIAL, application = CreditNoteApplication.REFUND_TO_PAYMENT,
                status = CreditNoteStatus.Applied, refundTransactionId = "ref_1",
                createdAt = now, updatedAt = now,
            )
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns listOf(existingCredit).right()

            useCase.execute(1L, "PARTIAL", "REFUND_TO_PAYMENT", BigDecimal("10.00"), "Reason").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.AmountExceedsRemaining>()
        }

        // CN-E7
        it("returns error for partial zero amount") {
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns emptyList<CreditNote>().right()

            useCase.execute(1L, "PARTIAL", "REFUND_TO_PAYMENT", BigDecimal("0.00"), "Reason").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.InvalidInput>()
        }

        // CN-E9
        it("returns error for blank reason") {
            useCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.InvalidInput>()
        }

        // CN-E10
        it("returns error when gateway refund fails") {
            every { invoiceCommandQueryPort.findById(invoiceId) } returns paidInvoice().right()
            every { creditNoteCommandQueryPort.findByInvoiceId(invoiceId) } returns emptyList<CreditNote>().right()
            every { paymentGatewayPort.refund(any(), any()) } returns PaymentError.Declined("Failed").left()
            every { creditNoteRepository.save(any()) } answers {
                firstArg<CreditNote>().copy(id = CreditNoteId(1L).getOrNull()!!).right()
            }

            useCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.PaymentFailed>()
        }

        // CN-E11
        it("returns error for invalid invoice ID") {
            useCase.execute(0L, "FULL", "REFUND_TO_PAYMENT", null, "Reason").shouldBeLeft().shouldBeInstanceOf<IssueCreditNoteError.InvalidInput>()
        }
    }
})
