package com.wakita181009.clean.application.command.usecase

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.error.DetachAddOnError
import com.wakita181009.clean.application.command.port.AddOnQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionAddOnCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.AddOn
import com.wakita181009.clean.domain.model.AddOnBillingType
import com.wakita181009.clean.domain.model.AddOnId
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
import com.wakita181009.clean.domain.model.SubscriptionAddOn
import com.wakita181009.clean.domain.model.SubscriptionAddOnId
import com.wakita181009.clean.domain.model.SubscriptionAddOnStatus
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionAddOnRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant

class DetachAddOnUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val addOnQueryPort = mockk<AddOnQueryPort>()
    val subscriptionAddOnCommandQueryPort = mockk<SubscriptionAddOnCommandQueryPort>()
    val subscriptionAddOnRepository = mockk<SubscriptionAddOnRepository>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = DetachAddOnUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        addOnQueryPort = addOnQueryPort,
        subscriptionAddOnCommandQueryPort = subscriptionAddOnCommandQueryPort,
        subscriptionAddOnRepository = subscriptionAddOnRepository,
        invoiceRepository = invoiceRepository,
        subscriptionRepository = subscriptionRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val periodStart = Instant.parse("2025-01-01T00:00:00Z")
    val periodEnd = Instant.parse("2025-01-31T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!
    val addOnIdVal = AddOnId(1L).getOrNull()!!

    val plan = Plan.of(
        id = PlanId(1L).getOrNull()!!, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
    ).getOrNull()!!

    fun activeSubscription(seatCount: Int? = null, status: SubscriptionStatus = SubscriptionStatus.Active) = Subscription(
        id = subId, customerId = CustomerId(1L).getOrNull()!!, plan = plan,
        status = status,
        currentPeriodStart = periodStart, currentPeriodEnd = periodEnd,
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD, createdAt = periodStart, updatedAt = periodStart,
        seatCount = seatCount,
    )

    val existingSubAddOn = SubscriptionAddOn(
        id = SubscriptionAddOnId(1L).getOrNull()!!, subscriptionId = subId,
        addOnId = addOnIdVal, quantity = 1, status = SubscriptionAddOnStatus.Active,
        attachedAt = periodStart, detachedAt = null,
    )

    val flatAddOn = AddOn.of(
        id = addOnIdVal, name = "Priority Support",
        price = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!,
        billingType = AddOnBillingType.FLAT,
        compatibleTiers = setOf(PlanTier.PROFESSIONAL), active = true,
    ).getOrNull()!!

    beforeTest {
        clearMocks(
            subscriptionCommandQueryPort, addOnQueryPort, subscriptionAddOnCommandQueryPort,
            subscriptionAddOnRepository, invoiceRepository, subscriptionRepository, clockPort, transactionPort,
        )
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
    }

    describe("Happy path") {
        // DA-H1
        it("detaches FLAT add-on mid-cycle") {
            val sub = activeSubscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (existingSubAddOn as SubscriptionAddOn?).right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (flatAddOn as AddOn?).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = com.wakita181009.clean.domain.model.InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg<SubscriptionAddOn>().right() }

            val result = useCase.execute(1L, 1L).shouldBeRight()
            result.status.shouldBeInstanceOf<SubscriptionAddOnStatus.Detached>()
        }

        // DA-H2
        it("detaches PER_SEAT add-on with correct credit") {
            val perSeatAddOn = AddOn.of(
                id = addOnIdVal, name = "Storage",
                price = Money.of(BigDecimal("2.00"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.PER_SEAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL), active = true,
            ).getOrNull()!!
            val subAddOn = existingSubAddOn.copy(quantity = 5)
            val sub = activeSubscription(seatCount = 5)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (subAddOn as SubscriptionAddOn?).right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (perSeatAddOn as AddOn?).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = com.wakita181009.clean.domain.model.InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg<SubscriptionAddOn>().right() }

            useCase.execute(1L, 1L).shouldBeRight()
        }

        // DA-H4
        it("detaches while Paused") {
            val sub = activeSubscription(status = SubscriptionStatus.Paused(now))
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (existingSubAddOn as SubscriptionAddOn?).right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (flatAddOn as AddOn?).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = com.wakita181009.clean.domain.model.InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg<SubscriptionAddOn>().right() }

            useCase.execute(1L, 1L).shouldBeRight()
        }
    }

    describe("Validation errors") {
        // DA-E1
        it("returns error when subscription not found") {
            every { subscriptionCommandQueryPort.findById(subId) } returns (object : DomainError { override val message = "not found" }).left()
            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<DetachAddOnError.SubscriptionNotFound>()
        }

        // DA-E2
        it("returns error when subscription in Trial") {
            val sub = activeSubscription(status = SubscriptionStatus.Trial)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<DetachAddOnError.InvalidStatus>()
        }

        // DA-E3
        it("returns error when subscription Canceled") {
            val sub = activeSubscription(status = SubscriptionStatus.Canceled(now))
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<DetachAddOnError.InvalidStatus>()
        }

        // DA-E4
        it("returns error when add-on not attached") {
            val sub = activeSubscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (null as SubscriptionAddOn?).right()
            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<DetachAddOnError.AddOnNotAttached>()
        }

        // DA-E6
        it("returns error for invalid subscription ID") {
            useCase.execute(0L, 1L).shouldBeLeft().shouldBeInstanceOf<DetachAddOnError.InvalidInput>()
        }

        // DA-E7
        it("returns error for invalid add-on ID") {
            useCase.execute(1L, -1L).shouldBeLeft().shouldBeInstanceOf<DetachAddOnError.InvalidInput>()
        }
    }
})
