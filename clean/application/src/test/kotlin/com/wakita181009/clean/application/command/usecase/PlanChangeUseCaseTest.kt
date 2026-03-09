package com.wakita181009.clean.application.command.usecase

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.dto.ChangePlanCommand
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.application.command.error.PlanChangeError
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.PlanQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Invoice
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
import com.wakita181009.clean.domain.service.ProrationDomainService
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

class PlanChangeUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val planQueryPort = mockk<PlanQueryPort>()
    val paymentGatewayPort = mockk<PaymentGatewayPort>()
    val prorationDomainService = ProrationDomainService()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = PlanChangeUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        planQueryPort = planQueryPort,
        paymentGatewayPort = paymentGatewayPort,
        prorationDomainService = prorationDomainService,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!
    val starterPlanId = PlanId(1L).getOrNull()!!
    val proPlanId = PlanId(2L).getOrNull()!!

    val starterPlan = Plan.of(
        id = starterPlanId, name = "Starter", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("19.99"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("basic"), tier = PlanTier.STARTER, active = true,
    ).getOrNull()!!

    val proPlan = Plan.of(
        id = proPlanId, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
    ).getOrNull()!!

    fun activeSub() = Subscription(
        id = subId, customerId = CustomerId(1L).getOrNull()!!,
        plan = starterPlan, status = SubscriptionStatus.Active,
        currentPeriodStart = now.minus(Duration.ofDays(15)),
        currentPeriodEnd = now.plus(Duration.ofDays(15)),
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        createdAt = now.minus(Duration.ofDays(15)), updatedAt = now.minus(Duration.ofDays(15)),
    )

    beforeTest {
        clearMocks(subscriptionCommandQueryPort, planQueryPort, paymentGatewayPort, subscriptionRepository, invoiceRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
    }

    describe("Upgrade (immediate charge)") {
        // CP-U1
        it("upgrades mid-cycle with proration") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { planQueryPort.findActiveById(proPlanId) } returns proPlan.right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn-1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(ChangePlanCommand(1L, 2L)).shouldBeRight()
            result.plan.id shouldBe proPlanId
        }

        // CP-E7
        it("payment fails on upgrade: plan NOT changed") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { planQueryPort.findActiveById(proPlanId) } returns proPlan.right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns
                com.wakita181009.clean.application.command.dto.PaymentError.Declined("declined").left()

            val result = useCase.execute(ChangePlanCommand(1L, 2L)).shouldBeLeft()
            result.shouldBeInstanceOf<PlanChangeError.PaymentFailed>()
        }
    }

    describe("Downgrade") {
        // CP-D1
        it("downgrades mid-cycle with credit") {
            val proSub = activeSub().copy(plan = proPlan)
            every { subscriptionCommandQueryPort.findById(subId) } returns proSub.right()
            every { planQueryPort.findActiveById(starterPlanId) } returns starterPlan.right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            // No payment charge expected for downgrade (negative net amount)

            val result = useCase.execute(ChangePlanCommand(1L, 1L)).shouldBeRight()
            result.plan.id shouldBe starterPlanId
        }
    }

    describe("Errors") {
        // CP-E1
        it("subscription not found") {
            every { subscriptionCommandQueryPort.findById(subId) } returns
                arrow.core.Either.Left(object : com.wakita181009.clean.domain.error.DomainError {
                    override val message = "Not found"
                })

            useCase.execute(ChangePlanCommand(1L, 2L)).shouldBeLeft()
                .shouldBeInstanceOf<PlanChangeError.SubscriptionNotFound>()
        }

        // CP-E2
        it("subscription not Active") {
            val pausedSub = activeSub().copy(status = SubscriptionStatus.Paused(now))
            every { subscriptionCommandQueryPort.findById(subId) } returns pausedSub.right()

            useCase.execute(ChangePlanCommand(1L, 2L)).shouldBeLeft()
                .shouldBeInstanceOf<PlanChangeError.NotActive>()
        }

        // CP-E4
        it("same plan") {
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()

            useCase.execute(ChangePlanCommand(1L, 1L)).shouldBeLeft()
                .shouldBeInstanceOf<PlanChangeError.SamePlan>()
        }

        // CP-E6
        it("currency mismatch") {
            val eurPlan = Plan.of(
                id = PlanId(3L).getOrNull()!!, name = "Euro Pro", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("49.99"), Currency.EUR).getOrNull()!!,
                usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
            ).getOrNull()!!
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { planQueryPort.findActiveById(PlanId(3L).getOrNull()!!) } returns eurPlan.right()

            useCase.execute(ChangePlanCommand(1L, 3L)).shouldBeLeft()
                .shouldBeInstanceOf<PlanChangeError.CurrencyMismatch>()
        }

        // CP-E8
        it("invalid subscription ID") {
            useCase.execute(ChangePlanCommand(0L, 2L)).shouldBeLeft()
                .shouldBeInstanceOf<PlanChangeError.InvalidInput>()
        }
    }
})
