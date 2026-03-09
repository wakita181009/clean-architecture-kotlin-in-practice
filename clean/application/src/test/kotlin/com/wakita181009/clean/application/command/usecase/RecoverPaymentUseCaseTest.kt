package com.wakita181009.clean.application.command.usecase

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.application.command.error.RecoverPaymentError
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.BillingInterval
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
import com.wakita181009.clean.domain.repository.InvoiceRepository
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class RecoverPaymentUseCaseTest : DescribeSpec({

    val invoiceCommandQueryPort = mockk<InvoiceCommandQueryPort>()
    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val paymentGatewayPort = mockk<PaymentGatewayPort>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = RecoverPaymentUseCaseImpl(
        invoiceCommandQueryPort = invoiceCommandQueryPort,
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        paymentGatewayPort = paymentGatewayPort,
        invoiceRepository = invoiceRepository,
        subscriptionRepository = subscriptionRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val gracePeriodEnd = Instant.parse("2025-01-22T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!
    val invId = InvoiceId(1L).getOrNull()!!
    val total = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!
    val plan = Plan.of(
        id = PlanId(1L).getOrNull()!!,
        name = "Pro",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = total,
        usageLimit = null, features = setOf("api"),
        tier = PlanTier.PROFESSIONAL, active = true,
    ).getOrNull()!!

    fun openInvoice(attempts: Int = 1) = Invoice(
        id = invId,
        subscriptionId = subId,
        lineItems = emptyList(),
        subtotal = total,
        discountAmount = Money.zero(Currency.USD),
        total = total,
        currency = Currency.USD,
        status = InvoiceStatus.Open,
        dueDate = LocalDate.of(2025, 1, 15),
        paidAt = null,
        paymentAttemptCount = attempts,
        createdAt = now,
        updatedAt = now,
    )

    fun pastDueSub() = Subscription(
        id = subId,
        customerId = CustomerId(1L).getOrNull()!!,
        plan = plan,
        status = SubscriptionStatus.PastDue(gracePeriodEnd),
        currentPeriodStart = now.minus(Duration.ofDays(30)),
        currentPeriodEnd = now,
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = gracePeriodEnd, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD, createdAt = now, updatedAt = now,
    )

    beforeTest {
        clearMocks(invoiceCommandQueryPort, subscriptionCommandQueryPort, paymentGatewayPort, invoiceRepository, subscriptionRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
    }

    describe("Happy path") {
        // RP-H1
        it("successfully recovers payment") {
            every { invoiceCommandQueryPort.findById(invId) } returns openInvoice(1).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns pastDueSub().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn-1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.status.shouldBeInstanceOf<InvoiceStatus.Paid>()
            result.paidAt shouldBe now
        }
    }

    describe("Payment failure handling") {
        // RP-F1
        it("increments attempt count on 1st retry failure") {
            every { invoiceCommandQueryPort.findById(invId) } returns openInvoice(1).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns pastDueSub().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns com.wakita181009.clean.application.command.dto.PaymentError.Declined("declined").left()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }

            val result = useCase.execute(1L).shouldBeLeft()
            result.shouldBeInstanceOf<RecoverPaymentError.PaymentFailed>()
        }

        // RP-F3
        it("marks uncollectible and cancels after 3rd attempt") {
            every { invoiceCommandQueryPort.findById(invId) } returns openInvoice(2).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns pastDueSub().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns com.wakita181009.clean.application.command.dto.PaymentError.Declined("declined").left()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.status.shouldBeInstanceOf<InvoiceStatus.Uncollectible>()
            result.paymentAttemptCount shouldBe 3
        }
    }

    describe("Grace period") {
        // RP-G3
        it("rejects when grace period expired") {
            val expiredGrace = now.minus(Duration.ofDays(1))
            val sub = pastDueSub().copy(
                status = SubscriptionStatus.PastDue(expiredGrace),
                gracePeriodEnd = expiredGrace,
            )
            every { invoiceCommandQueryPort.findById(invId) } returns openInvoice(1).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeLeft()
            result.shouldBeInstanceOf<RecoverPaymentError.GracePeriodExpired>()
        }
    }

    describe("Validation errors") {
        // RP-E6
        it("rejects invalid invoice ID") {
            useCase.execute(0L).shouldBeLeft()
                .shouldBeInstanceOf<RecoverPaymentError.InvalidInput>()
        }

        // RP-E2
        it("rejects non-Open invoice (Paid)") {
            val paidInvoice = openInvoice().copy(status = InvoiceStatus.Paid(now))
            every { invoiceCommandQueryPort.findById(invId) } returns paidInvoice.right()

            useCase.execute(1L).shouldBeLeft()
                .shouldBeInstanceOf<RecoverPaymentError.InvoiceNotOpen>()
        }

        // RP-E5
        it("rejects non-PastDue subscription") {
            val activeSub = pastDueSub().copy(status = SubscriptionStatus.Active, gracePeriodEnd = null)
            every { invoiceCommandQueryPort.findById(invId) } returns openInvoice().right()
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub.right()

            useCase.execute(1L).shouldBeLeft()
                .shouldBeInstanceOf<RecoverPaymentError.SubscriptionNotPastDue>()
        }
    }
})
