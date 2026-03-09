package com.wakita181009.clean.application.command.usecase

import arrow.core.right
import com.wakita181009.clean.application.command.dto.CancelSubscriptionCommand
import com.wakita181009.clean.application.command.error.CancelSubscriptionError
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
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

class CancelSubscriptionUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val invoiceCommandQueryPort = mockk<InvoiceCommandQueryPort>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = CancelSubscriptionUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        invoiceCommandQueryPort = invoiceCommandQueryPort,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!
    val plan = Plan.of(
        id = PlanId(1L).getOrNull()!!,
        name = "Pro",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = null,
        features = setOf("api"),
        tier = PlanTier.PROFESSIONAL,
        active = true,
    ).getOrNull()!!

    fun activeSub() = Subscription(
        id = subId,
        customerId = CustomerId(1L).getOrNull()!!,
        plan = plan,
        status = SubscriptionStatus.Active,
        currentPeriodStart = now.minus(Duration.ofDays(10)),
        currentPeriodEnd = now.plus(Duration.ofDays(20)),
        trialStart = null,
        trialEnd = null,
        pausedAt = null,
        canceledAt = null,
        cancelAtPeriodEnd = false,
        gracePeriodEnd = null,
        pauseCountInPeriod = 0,
        paymentMethod = null,
        createdAt = now.minus(Duration.ofDays(10)),
        updatedAt = now.minus(Duration.ofDays(10)),
    )

    beforeTest {
        clearMocks(subscriptionCommandQueryPort, invoiceCommandQueryPort, subscriptionRepository, invoiceRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers {
            firstArg<() -> Any>()()
        }
    }

    describe("End-of-period cancellation") {
        // CA-H1
        it("cancels Active at period end") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(CancelSubscriptionCommand(1L, immediate = false)).shouldBeRight()
            result.cancelAtPeriodEnd shouldBe true
            result.canceledAt shouldBe now
            result.status shouldBe SubscriptionStatus.Active // stays Active
        }

        // CA-E5
        it("rejects end-of-period for Paused subscription") {
            val pausedSub = activeSub().copy(status = SubscriptionStatus.Paused(now), pausedAt = now)
            every { subscriptionCommandQueryPort.findById(subId) } returns pausedSub.right()

            val result = useCase.execute(CancelSubscriptionCommand(1L, immediate = false)).shouldBeLeft()
            result.shouldBeInstanceOf<CancelSubscriptionError.CannotEndOfPeriodForPaused>()
        }
    }

    describe("Immediate cancellation") {
        // CA-H3
        it("cancels Active immediately and voids open invoices") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { invoiceCommandQueryPort.findOpenBySubscriptionId(subId) } returns emptyList<Invoice>().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(CancelSubscriptionCommand(1L, immediate = true)).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionStatus.Canceled>()
            result.canceledAt shouldBe now
        }

        // CA-H4
        it("cancels Paused subscription immediately") {
            val pausedSub = activeSub().copy(status = SubscriptionStatus.Paused(now), pausedAt = now)
            every { subscriptionCommandQueryPort.findById(subId) } returns pausedSub.right()
            every { invoiceCommandQueryPort.findOpenBySubscriptionId(subId) } returns emptyList<Invoice>().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(CancelSubscriptionCommand(1L, immediate = true)).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionStatus.Canceled>()
        }

        // CA-H6
        it("cancels Trial subscription immediately") {
            val trialSub = activeSub().copy(status = SubscriptionStatus.Trial)
            every { subscriptionCommandQueryPort.findById(subId) } returns trialSub.right()
            every { invoiceCommandQueryPort.findOpenBySubscriptionId(subId) } returns emptyList<Invoice>().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(CancelSubscriptionCommand(1L, immediate = true)).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionStatus.Canceled>()
        }
    }

    describe("Errors") {
        // CA-E2
        it("rejects already Canceled") {
            val canceledSub = activeSub().copy(status = SubscriptionStatus.Canceled(now))
            every { subscriptionCommandQueryPort.findById(subId) } returns canceledSub.right()

            val result = useCase.execute(CancelSubscriptionCommand(1L, immediate = true)).shouldBeLeft()
            result.shouldBeInstanceOf<CancelSubscriptionError.AlreadyTerminal>()
        }

        // CA-E3
        it("rejects already Expired") {
            val expiredSub = activeSub().copy(status = SubscriptionStatus.Expired)
            every { subscriptionCommandQueryPort.findById(subId) } returns expiredSub.right()

            val result = useCase.execute(CancelSubscriptionCommand(1L, immediate = true)).shouldBeLeft()
            result.shouldBeInstanceOf<CancelSubscriptionError.AlreadyTerminal>()
        }

        // CA-E4
        it("rejects invalid subscription ID") {
            val result = useCase.execute(CancelSubscriptionCommand(-1L, immediate = false)).shouldBeLeft()
            result.shouldBeInstanceOf<CancelSubscriptionError.InvalidInput>()
        }
    }
})
