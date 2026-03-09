package com.wakita181009.clean.presentation.controller

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.dto.CancelSubscriptionCommand
import com.wakita181009.clean.application.command.dto.ChangePlanCommand
import com.wakita181009.clean.application.command.dto.CreateSubscriptionCommand
import com.wakita181009.clean.application.command.dto.RecordUsageCommand
import com.wakita181009.clean.application.command.error.CancelSubscriptionError
import com.wakita181009.clean.application.command.error.PauseSubscriptionError
import com.wakita181009.clean.application.command.error.PlanChangeError
import com.wakita181009.clean.application.command.error.RecordUsageError
import com.wakita181009.clean.application.command.error.ResumeSubscriptionError
import com.wakita181009.clean.application.command.error.SubscriptionCreateError
import com.wakita181009.clean.application.command.usecase.CancelSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.PauseSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.PlanChangeUseCase
import com.wakita181009.clean.application.command.usecase.RecordUsageUseCase
import com.wakita181009.clean.application.command.usecase.ResumeSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.SubscriptionCreateUseCase
import com.wakita181009.clean.application.query.dto.SubscriptionDto
import com.wakita181009.clean.application.query.error.SubscriptionFindByIdQueryError
import com.wakita181009.clean.application.query.error.SubscriptionListByCustomerQueryError
import com.wakita181009.clean.application.query.usecase.SubscriptionFindByIdQueryUseCase
import com.wakita181009.clean.application.query.usecase.SubscriptionListByCustomerQueryUseCase
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.IdempotencyKey
import com.wakita181009.clean.domain.model.MetricName
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.model.UsageId
import com.wakita181009.clean.domain.model.UsageRecord
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class SubscriptionControllerTest : DescribeSpec({

    val subscriptionCreateUseCase = mockk<SubscriptionCreateUseCase>()
    val planChangeUseCase = mockk<PlanChangeUseCase>()
    val pauseSubscriptionUseCase = mockk<PauseSubscriptionUseCase>()
    val resumeSubscriptionUseCase = mockk<ResumeSubscriptionUseCase>()
    val cancelSubscriptionUseCase = mockk<CancelSubscriptionUseCase>()
    val recordUsageUseCase = mockk<RecordUsageUseCase>()
    val attachAddOnUseCase = mockk<com.wakita181009.clean.application.command.usecase.AttachAddOnUseCase>()
    val detachAddOnUseCase = mockk<com.wakita181009.clean.application.command.usecase.DetachAddOnUseCase>()
    val updateSeatCountUseCase = mockk<com.wakita181009.clean.application.command.usecase.UpdateSeatCountUseCase>()
    val subscriptionFindByIdQueryUseCase = mockk<SubscriptionFindByIdQueryUseCase>()
    val subscriptionListByCustomerQueryUseCase = mockk<SubscriptionListByCustomerQueryUseCase>()
    val subscriptionAddOnListQueryUseCase = mockk<com.wakita181009.clean.application.query.usecase.SubscriptionAddOnListQueryUseCase>()

    val controller = SubscriptionController(
        subscriptionCreateUseCase = subscriptionCreateUseCase,
        planChangeUseCase = planChangeUseCase,
        pauseSubscriptionUseCase = pauseSubscriptionUseCase,
        resumeSubscriptionUseCase = resumeSubscriptionUseCase,
        cancelSubscriptionUseCase = cancelSubscriptionUseCase,
        recordUsageUseCase = recordUsageUseCase,
        attachAddOnUseCase = attachAddOnUseCase,
        detachAddOnUseCase = detachAddOnUseCase,
        updateSeatCountUseCase = updateSeatCountUseCase,
        subscriptionFindByIdQueryUseCase = subscriptionFindByIdQueryUseCase,
        subscriptionListByCustomerQueryUseCase = subscriptionListByCustomerQueryUseCase,
        subscriptionAddOnListQueryUseCase = subscriptionAddOnListQueryUseCase,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val plan = Plan.of(
        id = PlanId(1L).getOrNull()!!,
        name = "Pro",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"),
        tier = PlanTier.PROFESSIONAL, active = true,
    ).getOrNull()!!

    fun subscription() = Subscription(
        id = SubscriptionId(1L).getOrNull()!!,
        customerId = CustomerId(1L).getOrNull()!!,
        plan = plan,
        status = SubscriptionStatus.Trial,
        currentPeriodStart = now,
        currentPeriodEnd = now.plus(Duration.ofDays(14)),
        trialStart = now, trialEnd = now.plus(Duration.ofDays(14)),
        pausedAt = null, canceledAt = null, cancelAtPeriodEnd = false,
        gracePeriodEnd = null, pauseCountInPeriod = 0, paymentMethod = null,
        createdAt = now, updatedAt = now,
    )

    fun subscriptionDto() = SubscriptionDto(
        id = 1L, customerId = 1L, planId = 1L, planName = "Pro",
        planTier = "PROFESSIONAL", planBillingInterval = "MONTHLY",
        planBasePriceAmount = "49.99", planBasePriceCurrency = "USD",
        status = "ACTIVE", currentPeriodStart = now, currentPeriodEnd = now,
        trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, discountType = null,
        discountValue = null, discountRemainingCycles = null,
        createdAt = now, updatedAt = now,
    )

    beforeTest {
        clearMocks(
            subscriptionCreateUseCase, planChangeUseCase, pauseSubscriptionUseCase,
            resumeSubscriptionUseCase, cancelSubscriptionUseCase, recordUsageUseCase,
            subscriptionFindByIdQueryUseCase, subscriptionListByCustomerQueryUseCase,
        )
    }

    describe("POST /api/subscriptions (create)") {
        // API-CS1
        it("returns 201 on successful creation") {
            every { subscriptionCreateUseCase.execute(any()) } returns subscription().right()
            val request = com.wakita181009.clean.presentation.dto.CreateSubscriptionRequest(
                customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null,
            )
            val response = controller.create(request)
            response.statusCode shouldBe HttpStatus.CREATED
        }

        // API-CS2
        it("returns 400 on validation failure") {
            every { subscriptionCreateUseCase.execute(any()) } returns
                SubscriptionCreateError.InvalidInput("customerId", "must be positive").left()
            val request = com.wakita181009.clean.presentation.dto.CreateSubscriptionRequest(
                customerId = 0L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null,
            )
            val response = controller.create(request)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        // API-CS3
        it("returns 409 on duplicate subscription") {
            every { subscriptionCreateUseCase.execute(any()) } returns
                SubscriptionCreateError.AlreadySubscribed.left()
            val request = com.wakita181009.clean.presentation.dto.CreateSubscriptionRequest(
                customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", discountCode = null,
            )
            val response = controller.create(request)
            response.statusCode shouldBe HttpStatus.CONFLICT
        }
    }

    describe("POST /api/subscriptions/{id}/pause") {
        // API-PA1
        it("returns 200 on successful pause") {
            val pausedSub = subscription().copy(status = SubscriptionStatus.Paused(now), pausedAt = now)
            every { pauseSubscriptionUseCase.execute(1L) } returns pausedSub.right()
            val response = controller.pause(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-PA2
        it("returns 409 when pause limit reached") {
            every { pauseSubscriptionUseCase.execute(1L) } returns PauseSubscriptionError.PauseLimitReached.left()
            val response = controller.pause(1L)
            response.statusCode shouldBe HttpStatus.CONFLICT
        }
    }

    describe("POST /api/subscriptions/{id}/resume") {
        // API-RE1
        it("returns 200 on successful resume") {
            val activeSub = subscription().copy(status = SubscriptionStatus.Active)
            every { resumeSubscriptionUseCase.execute(1L) } returns activeSub.right()
            val response = controller.resume(1L)
            response.statusCode shouldBe HttpStatus.OK
        }
    }

    describe("POST /api/subscriptions/{id}/cancel") {
        // API-CA1
        it("returns 200 on end-of-period cancel") {
            val sub = subscription().copy(cancelAtPeriodEnd = true)
            every { cancelSubscriptionUseCase.execute(any()) } returns sub.right()
            val request = com.wakita181009.clean.presentation.dto.CancelSubscriptionRequest(immediate = false)
            val response = controller.cancel(1L, request)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-CA3
        it("returns 409 on already canceled") {
            every { cancelSubscriptionUseCase.execute(any()) } returns CancelSubscriptionError.AlreadyTerminal.left()
            val request = com.wakita181009.clean.presentation.dto.CancelSubscriptionRequest(immediate = true)
            val response = controller.cancel(1L, request)
            response.statusCode shouldBe HttpStatus.CONFLICT
        }
    }

    describe("POST /api/subscriptions/{id}/usage") {
        // API-US1
        it("returns 201 on successful usage recording") {
            val record = UsageRecord(
                id = UsageId(1L).getOrNull()!!,
                subscriptionId = SubscriptionId(1L).getOrNull()!!,
                metricName = MetricName("api_calls").getOrNull()!!,
                quantity = 100, recordedAt = now,
                idempotencyKey = IdempotencyKey("req-1").getOrNull()!!,
            )
            every { recordUsageUseCase.execute(any()) } returns record.right()
            val request = com.wakita181009.clean.presentation.dto.RecordUsageRequest(
                metricName = "api_calls", quantity = 100, idempotencyKey = "req-1",
            )
            val response = controller.recordUsage(1L, request)
            response.statusCode shouldBe HttpStatus.CREATED
        }

        // API-US3
        it("returns 409 when usage limit exceeded") {
            every { recordUsageUseCase.execute(any()) } returns
                RecordUsageError.UsageLimitExceeded(9950L, 10000, 51).left()
            val request = com.wakita181009.clean.presentation.dto.RecordUsageRequest(
                metricName = "api_calls", quantity = 51, idempotencyKey = "req-2",
            )
            val response = controller.recordUsage(1L, request)
            response.statusCode shouldBe HttpStatus.CONFLICT
        }
    }

    describe("GET /api/subscriptions/{id}") {
        // API-G1
        it("returns 200 with subscription") {
            every { subscriptionFindByIdQueryUseCase.execute(1L) } returns subscriptionDto().right()
            val response = controller.findById(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-G2
        it("returns 404 when not found") {
            every { subscriptionFindByIdQueryUseCase.execute(99999L) } returns
                SubscriptionFindByIdQueryError.NotFound.left()
            val response = controller.findById(99999L)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        // API-G5
        it("returns 400 for invalid ID") {
            every { subscriptionFindByIdQueryUseCase.execute(-1L) } returns
                SubscriptionFindByIdQueryError.InvalidInput("id", "must be positive").left()
            val response = controller.findById(-1L)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }

    describe("GET /api/subscriptions?customerId=") {
        // API-G3
        it("returns 200 with subscription list") {
            every { subscriptionListByCustomerQueryUseCase.execute(1L) } returns listOf(subscriptionDto()).right()
            val response = controller.listByCustomer(1L)
            response.statusCode shouldBe HttpStatus.OK
        }
    }
})
