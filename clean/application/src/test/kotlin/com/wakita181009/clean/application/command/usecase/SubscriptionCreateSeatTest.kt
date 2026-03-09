package com.wakita181009.clean.application.command.usecase

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
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
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
import java.time.Instant

class SubscriptionCreateSeatTest : DescribeSpec({

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

    val perSeatPlan = Plan.of(
        id = planId, name = "Pro Seats", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
        perSeatPricing = true, minSeats = 1, maxSeats = 50,
    ).getOrNull()!!

    val perSeatPlanMin3 = Plan.of(
        id = planId, name = "Pro Seats", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
        perSeatPricing = true, minSeats = 3, maxSeats = 10,
    ).getOrNull()!!

    val nonPerSeatPlan = Plan.of(
        id = planId, name = "Basic", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("basic"), tier = PlanTier.STARTER, active = true,
    ).getOrNull()!!

    beforeTest {
        clearMocks(subscriptionRepository, subscriptionCommandQueryPort, planQueryPort, discountCodePort, discountRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
        every { subscriptionCommandQueryPort.findActiveByCustomerId(any()) } returns (null as Subscription?).right()
    }

    describe("Create subscription with seats") {
        // CS-S1
        it("creates with per-seat plan and seat count") {
            every { planQueryPort.findActiveById(planId) } returns (perSeatPlan as Plan?).right()
            every { subscriptionRepository.save(any()) } answers {
                firstArg<Subscription>().copy(id = SubscriptionId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null, seatCount = 5),
            ).shouldBeRight()
            result.seatCount shouldBe 5
        }

        // CS-S2
        it("creates per-seat at minimum") {
            every { planQueryPort.findActiveById(planId) } returns (perSeatPlanMin3 as Plan?).right()
            every { subscriptionRepository.save(any()) } answers {
                firstArg<Subscription>().copy(id = SubscriptionId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null, seatCount = 3),
            ).shouldBeRight()
            result.seatCount shouldBe 3
        }

        // CS-S3
        it("rejects per-seat below minimum") {
            every { planQueryPort.findActiveById(planId) } returns (perSeatPlanMin3 as Plan?).right()

            useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null, seatCount = 2),
            ).shouldBeLeft().shouldBeInstanceOf<SubscriptionCreateError.InvalidInput>()
        }

        // CS-S4
        it("rejects per-seat above maximum") {
            every { planQueryPort.findActiveById(planId) } returns (perSeatPlanMin3 as Plan?).right()

            useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null, seatCount = 11),
            ).shouldBeLeft().shouldBeInstanceOf<SubscriptionCreateError.InvalidInput>()
        }

        // CS-S5
        it("rejects per-seat plan without seat count") {
            every { planQueryPort.findActiveById(planId) } returns (perSeatPlan as Plan?).right()

            useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null, seatCount = null),
            ).shouldBeLeft().shouldBeInstanceOf<SubscriptionCreateError.InvalidInput>()
        }

        // CS-S6
        it("creates non-per-seat ignoring seat count") {
            every { planQueryPort.findActiveById(planId) } returns (nonPerSeatPlan as Plan?).right()
            every { subscriptionRepository.save(any()) } answers {
                firstArg<Subscription>().copy(id = SubscriptionId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(
                CreateSubscriptionCommand(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null, seatCount = 5),
            ).shouldBeRight()
            result.seatCount shouldBe null
        }
    }
})
