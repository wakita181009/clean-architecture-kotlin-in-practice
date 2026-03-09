package com.wakita181009.clean.application.command.usecase

import arrow.core.right
import com.wakita181009.clean.application.command.error.PauseSubscriptionError
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

class PauseSubscriptionUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = PauseSubscriptionUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        subscriptionRepository = subscriptionRepository,
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

    fun activeSubscription(pauseCount: Int = 0) = Subscription(
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
        pauseCountInPeriod = pauseCount,
        paymentMethod = null,
        createdAt = now.minus(Duration.ofDays(10)),
        updatedAt = now.minus(Duration.ofDays(10)),
    )

    beforeTest {
        clearMocks(subscriptionCommandQueryPort, subscriptionRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers {
            firstArg<() -> Any>()()
        }
    }

    describe("Happy path") {
        // PA-H1
        it("pauses active subscription (first pause)") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSubscription(0).right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionStatus.Paused>()
            result.pausedAt shouldBe now
            result.pauseCountInPeriod shouldBe 1
        }

        // PA-H2
        it("pauses active subscription (second pause, at limit)") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSubscription(1).right()
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionStatus.Paused>()
            result.pauseCountInPeriod shouldBe 2
        }
    }

    describe("Errors") {
        // PA-E6
        it("rejects when pause limit reached") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSubscription(2).right()

            val result = useCase.execute(1L).shouldBeLeft()
            result.shouldBeInstanceOf<PauseSubscriptionError.PauseLimitReached>()
        }

        // PA-E2
        it("rejects non-Active status (Trial)") {
            val trialSub = activeSubscription().copy(status = SubscriptionStatus.Trial)
            every { subscriptionCommandQueryPort.findById(subId) } returns trialSub.right()

            val result = useCase.execute(1L).shouldBeLeft()
            result.shouldBeInstanceOf<PauseSubscriptionError.NotActive>()
        }

        // PA-E7
        it("rejects invalid subscription ID") {
            val result = useCase.execute(-1L).shouldBeLeft()
            result.shouldBeInstanceOf<PauseSubscriptionError.InvalidInput>()
        }
    }
})
