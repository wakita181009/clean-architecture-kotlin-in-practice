package com.wakita181009.clean.application.command.usecase

import arrow.core.right
import com.wakita181009.clean.application.command.error.ResumeSubscriptionError
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
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

class ResumeSubscriptionUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = ResumeSubscriptionUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        subscriptionRepository = subscriptionRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-20T00:00:00Z")
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

    val pausedAt = Instant.parse("2025-01-15T00:00:00Z")
    val periodEnd = Instant.parse("2025-02-04T00:00:00Z") // 20 days remaining from pausedAt

    fun pausedSubscription() = Subscription(
        id = subId,
        customerId = CustomerId(1L).getOrNull()!!,
        plan = plan,
        status = SubscriptionStatus.Paused(pausedAt),
        currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
        currentPeriodEnd = periodEnd,
        trialStart = null,
        trialEnd = null,
        pausedAt = pausedAt,
        canceledAt = null,
        cancelAtPeriodEnd = false,
        gracePeriodEnd = null,
        pauseCountInPeriod = 1,
        paymentMethod = null,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = pausedAt,
    )

    beforeTest {
        clearMocks(subscriptionCommandQueryPort, subscriptionRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers {
            firstArg<() -> Any>()()
        }
    }

    describe("Happy path") {
        // RE-H1
        it("resumes after short pause, preserving remaining days") {
            every { subscriptionCommandQueryPort.findById(subId) } returns pausedSubscription().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.status shouldBe SubscriptionStatus.Active
            result.pausedAt shouldBe null
            // Remaining days from pause: Duration(pausedAt, periodEnd) = 20 days
            // newPeriodEnd = now + 20 days = 2025-02-09
            result.currentPeriodEnd shouldBe now.plus(Duration.ofDays(20))
        }
    }

    describe("Period calculation") {
        // RE-P1
        it("remaining days preserved regardless of pause duration") {
            every { subscriptionCommandQueryPort.findById(subId) } returns pausedSubscription().right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            // Paused 5 days ago with 20 days remaining -> periodEnd = now + 20 days
            result.currentPeriodEnd shouldBe now.plus(Duration.ofDays(20))
        }
    }

    describe("Errors") {
        // RE-E2
        it("rejects non-Paused status (Active)") {
            val activeSub = pausedSubscription().copy(status = SubscriptionStatus.Active, pausedAt = null)
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub.right()

            val result = useCase.execute(1L).shouldBeLeft()
            result.shouldBeInstanceOf<ResumeSubscriptionError.NotPaused>()
        }

        // RE-E6
        it("rejects invalid subscription ID") {
            val result = useCase.execute(0L).shouldBeLeft()
            result.shouldBeInstanceOf<ResumeSubscriptionError.InvalidInput>()
        }
    }
})
