package com.wakita181009.clean.application.command.usecase

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.application.command.error.ProcessRenewalError
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.command.port.UsageQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Discount
import com.wakita181009.clean.domain.model.DiscountType
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.PaymentMethod
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.model.UsageRecord
import com.wakita181009.clean.domain.repository.DiscountRepository
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

class ProcessRenewalUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val usageQueryPort = mockk<UsageQueryPort>()
    val paymentGatewayPort = mockk<PaymentGatewayPort>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val discountRepository = mockk<DiscountRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = ProcessRenewalUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        usageQueryPort = usageQueryPort,
        paymentGatewayPort = paymentGatewayPort,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        discountRepository = discountRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-02-01T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!
    val plan = Plan.of(
        id = PlanId(1L).getOrNull()!!, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = 10000, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
    ).getOrNull()!!

    fun activeSub(cancelAtPeriodEnd: Boolean = false) = Subscription(
        id = subId, customerId = CustomerId(1L).getOrNull()!!,
        plan = plan, status = SubscriptionStatus.Active,
        currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
        currentPeriodEnd = now, // period has ended
        trialStart = null, trialEnd = null, pausedAt = null,
        canceledAt = if (cancelAtPeriodEnd) now else null,
        cancelAtPeriodEnd = cancelAtPeriodEnd,
        gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )

    beforeTest {
        clearMocks(subscriptionCommandQueryPort, usageQueryPort, paymentGatewayPort, subscriptionRepository, invoiceRepository, discountRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
    }

    describe("Happy path") {
        // RN-H1
        it("simple monthly renewal with payment success") {
            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(activeSub(), null).right()
            every { usageQueryPort.findForPeriod(any(), any(), any()) } returns emptyList<UsageRecord>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn-1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.currentPeriodStart shouldBe now
            // Period advanced by 1 month
            result.currentPeriodEnd shouldBe BillingInterval.MONTHLY.nextPeriodEnd(now)
            result.pauseCountInPeriod shouldBe 0
        }

        // RN-H3 - with discount
        it("renewal with active discount") {
            val discount = Discount.of(
                id = null, subscriptionId = subId, type = DiscountType.PERCENTAGE,
                percentageValue = 20, fixedAmountMoney = null,
                durationMonths = 3, remainingCycles = 2, appliedAt = now,
            ).getOrNull()!!
            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(activeSub(), discount).right()
            every { usageQueryPort.findForPeriod(any(), any(), any()) } returns emptyList<UsageRecord>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn-1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            every { discountRepository.save(any()) } answers { firstArg<Discount>().right() }

            useCase.execute(1L).shouldBeRight()
        }

        // EP-1 - cancel at period end
        it("cancels subscription when cancelAtPeriodEnd is true") {
            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(activeSub(true), null).right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionStatus.Canceled>()
        }
    }

    describe("Payment failure -> PastDue") {
        // RN-F1
        it("moves to PastDue on payment failure") {
            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(activeSub(), null).right()
            every { usageQueryPort.findForPeriod(any(), any(), any()) } returns emptyList<UsageRecord>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns
                com.wakita181009.clean.application.command.dto.PaymentError.Declined("declined").left()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionStatus.PastDue>()
            result.gracePeriodEnd shouldBe now.plus(Duration.ofDays(7))
        }
    }

    describe("Skip conditions") {
        // RN-S1
        it("no-op when period not ended") {
            val notDueSub = activeSub().copy(currentPeriodEnd = now.plus(Duration.ofDays(10)))
            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(notDueSub, null).right()

            useCase.execute(1L).shouldBeLeft()
                .shouldBeInstanceOf<ProcessRenewalError.NotDue>()
        }

        // RN-S2
        it("no-op for non-Active status") {
            val pausedSub = activeSub().copy(status = SubscriptionStatus.Paused(now))
            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(pausedSub, null).right()

            useCase.execute(1L).shouldBeLeft()
                .shouldBeInstanceOf<ProcessRenewalError.NotDue>()
        }
    }

    describe("Free tier renewal") {
        // RN-H5
        it("auto-marks zero total invoice as paid") {
            val freePlan = Plan.of(
                id = PlanId(2L).getOrNull()!!, name = "Free", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("0.00"), Currency.USD).getOrNull()!!,
                usageLimit = 100, features = setOf("basic"), tier = PlanTier.FREE, active = true,
            ).getOrNull()!!
            val freeSub = activeSub().copy(plan = freePlan)
            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(freeSub, null).right()
            every { usageQueryPort.findForPeriod(any(), any(), any()) } returns emptyList<UsageRecord>().right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.currentPeriodStart shouldBe now
        }
    }
})
