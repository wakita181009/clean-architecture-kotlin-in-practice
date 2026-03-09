package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.right
import com.wakita181009.clean.application.command.dto.CreateSubscriptionCommand
import com.wakita181009.clean.application.command.error.SubscriptionCreateError
import com.wakita181009.clean.application.command.port.DiscountCodePort
import com.wakita181009.clean.application.command.port.PlanQueryPort
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
import com.wakita181009.clean.domain.repository.DiscountRepository
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

class SubscriptionCreateUseCaseTest : DescribeSpec({

    val subscriptionRepository = mockk<SubscriptionRepository>()
    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val planQueryPort = mockk<PlanQueryPort>()
    val discountCodePort = mockk<DiscountCodePort>()
    val discountRepository = mockk<DiscountRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = SubscriptionCreateUseCaseImpl(
        subscriptionRepository = subscriptionRepository,
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        planQueryPort = planQueryPort,
        discountCodePort = discountCodePort,
        discountRepository = discountRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val planId = PlanId(1L).getOrNull()!!
    val proPlan = Plan.of(
        id = planId,
        name = "Pro",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = 10000,
        features = setOf("api_access"),
        tier = PlanTier.PROFESSIONAL,
        active = true,
    ).getOrNull()!!

    beforeTest {
        clearMocks(subscriptionRepository, subscriptionCommandQueryPort, planQueryPort, discountCodePort, discountRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers {
            val block = firstArg<() -> Any>()
            block()
        }
    }

    describe("Happy path") {
        // CS-H1
        it("creates subscription with trial") {
            every { planQueryPort.findActiveById(planId) } returns proPlan.right()
            every { subscriptionCommandQueryPort.findActiveByCustomerId(any()) } returns (null as Subscription?).right()
            every { subscriptionRepository.save(any()) } answers {
                val sub = firstArg<Subscription>()
                sub.copy(id = SubscriptionId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(
                CreateSubscriptionCommand(
                    customerId = 1L,
                    planId = 1L,
                    paymentMethod = "CREDIT_CARD",
                    discountCode = null,
                ),
            ).shouldBeRight()

            result.status shouldBe SubscriptionStatus.Trial
            result.trialEnd shouldBe now.plus(Duration.ofDays(14))
            result.plan.name shouldBe "Pro"
        }
    }

    describe("Validation errors") {
        // CS-V1
        it("rejects invalid customer ID (zero)") {
            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = 0L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null),
            ).shouldBeLeft()
            result.shouldBeInstanceOf<SubscriptionCreateError.InvalidInput>()
        }

        // CS-V2
        it("rejects invalid customer ID (negative)") {
            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = -1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null),
            ).shouldBeLeft()
            result.shouldBeInstanceOf<SubscriptionCreateError.InvalidInput>()
        }

        // CS-V3
        it("rejects invalid plan ID (zero)") {
            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 0L, paymentMethod = "CREDIT_CARD", discountCode = null),
            ).shouldBeLeft()
            result.shouldBeInstanceOf<SubscriptionCreateError.InvalidInput>()
        }
    }

    describe("Business rule errors") {
        // CS-B1
        it("returns PlanNotFound when plan does not exist") {
            every { planQueryPort.findActiveById(planId) } returns (null as Plan?).right()
            every { subscriptionCommandQueryPort.findActiveByCustomerId(any()) } returns (null as Subscription?).right()

            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null),
            ).shouldBeLeft()
            result.shouldBeInstanceOf<SubscriptionCreateError.PlanNotFound>()
        }

        // CS-B3
        it("rejects when customer already has active subscription") {
            every { planQueryPort.findActiveById(planId) } returns proPlan.right()
            val existingSub = Subscription(
                id = SubscriptionId(1L).getOrNull()!!,
                customerId = CustomerId(1L).getOrNull()!!,
                plan = proPlan,
                status = SubscriptionStatus.Active,
                currentPeriodStart = now,
                currentPeriodEnd = now.plus(Duration.ofDays(30)),
                trialStart = null,
                trialEnd = null,
                pausedAt = null,
                canceledAt = null,
                cancelAtPeriodEnd = false,
                gracePeriodEnd = null,
                pauseCountInPeriod = 0,
                paymentMethod = null,
                createdAt = now,
                updatedAt = now,
            )
            every { subscriptionCommandQueryPort.findActiveByCustomerId(any()) } returns existingSub.right()

            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null),
            ).shouldBeLeft()
            result.shouldBeInstanceOf<SubscriptionCreateError.AlreadySubscribed>()
        }
    }
})
