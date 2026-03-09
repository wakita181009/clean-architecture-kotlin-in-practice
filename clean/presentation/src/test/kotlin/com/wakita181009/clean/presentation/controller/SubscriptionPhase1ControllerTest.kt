package com.wakita181009.clean.presentation.controller

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.error.AttachAddOnError
import com.wakita181009.clean.application.command.error.DetachAddOnError
import com.wakita181009.clean.application.command.error.UpdateSeatCountError
import com.wakita181009.clean.application.command.usecase.AttachAddOnUseCase
import com.wakita181009.clean.application.command.usecase.CancelSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.DetachAddOnUseCase
import com.wakita181009.clean.application.command.usecase.PauseSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.PlanChangeUseCase
import com.wakita181009.clean.application.command.usecase.RecordUsageUseCase
import com.wakita181009.clean.application.command.usecase.ResumeSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.SubscriptionCreateUseCase
import com.wakita181009.clean.application.command.usecase.UpdateSeatCountUseCase
import com.wakita181009.clean.application.query.dto.SubscriptionAddOnDto
import com.wakita181009.clean.application.query.error.SubscriptionAddOnListQueryError
import com.wakita181009.clean.application.query.usecase.SubscriptionAddOnListQueryUseCase
import com.wakita181009.clean.application.query.usecase.SubscriptionFindByIdQueryUseCase
import com.wakita181009.clean.application.query.usecase.SubscriptionListByCustomerQueryUseCase
import com.wakita181009.clean.domain.model.AddOnId
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
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
import com.wakita181009.clean.presentation.dto.AttachAddOnRequest
import com.wakita181009.clean.presentation.dto.UpdateSeatCountRequest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class SubscriptionPhase1ControllerTest : DescribeSpec({

    val subscriptionCreateUseCase = mockk<SubscriptionCreateUseCase>()
    val planChangeUseCase = mockk<PlanChangeUseCase>()
    val pauseSubscriptionUseCase = mockk<PauseSubscriptionUseCase>()
    val resumeSubscriptionUseCase = mockk<ResumeSubscriptionUseCase>()
    val cancelSubscriptionUseCase = mockk<CancelSubscriptionUseCase>()
    val recordUsageUseCase = mockk<RecordUsageUseCase>()
    val attachAddOnUseCase = mockk<AttachAddOnUseCase>()
    val detachAddOnUseCase = mockk<DetachAddOnUseCase>()
    val updateSeatCountUseCase = mockk<UpdateSeatCountUseCase>()
    val subscriptionFindByIdQueryUseCase = mockk<SubscriptionFindByIdQueryUseCase>()
    val subscriptionListByCustomerQueryUseCase = mockk<SubscriptionListByCustomerQueryUseCase>()
    val subscriptionAddOnListQueryUseCase = mockk<SubscriptionAddOnListQueryUseCase>()

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
        id = PlanId(1L).getOrNull()!!, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
        perSeatPricing = true, minSeats = 1, maxSeats = 100,
    ).getOrNull()!!

    fun subscription() = Subscription(
        id = SubscriptionId(1L).getOrNull()!!, customerId = CustomerId(1L).getOrNull()!!,
        plan = plan, status = SubscriptionStatus.Active,
        currentPeriodStart = now, currentPeriodEnd = now.plus(Duration.ofDays(30)),
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD, createdAt = now, updatedAt = now,
        seatCount = 5,
    )

    fun subscriptionAddOn() = SubscriptionAddOn(
        id = SubscriptionAddOnId(1L).getOrNull()!!, subscriptionId = SubscriptionId(1L).getOrNull()!!,
        addOnId = AddOnId(1L).getOrNull()!!, quantity = 1,
        status = SubscriptionAddOnStatus.Active, attachedAt = now, detachedAt = null,
    )

    beforeTest {
        clearMocks(
            attachAddOnUseCase, detachAddOnUseCase, updateSeatCountUseCase,
            subscriptionAddOnListQueryUseCase,
        )
    }

    describe("Attach Add-on API") {
        // API-AO1
        it("returns 201 on successful attachment") {
            every { attachAddOnUseCase.execute(1L, 1L) } returns subscriptionAddOn().right()
            val response = controller.attachAddOn(1L, AttachAddOnRequest(addonId = 1L))
            response.statusCode shouldBe HttpStatus.CREATED
        }

        // API-AO2
        it("returns 409 when subscription not Active") {
            every { attachAddOnUseCase.execute(1L, 1L) } returns AttachAddOnError.NotActive.left()
            val response = controller.attachAddOn(1L, AttachAddOnRequest(addonId = 1L))
            response.statusCode shouldBe HttpStatus.CONFLICT
        }

        // API-AO3
        it("returns 409 when tier incompatible") {
            every { attachAddOnUseCase.execute(1L, 1L) } returns AttachAddOnError.TierIncompatible.left()
            val response = controller.attachAddOn(1L, AttachAddOnRequest(addonId = 1L))
            response.statusCode shouldBe HttpStatus.CONFLICT
        }

        // API-AO4
        it("returns 502 on payment failure") {
            every { attachAddOnUseCase.execute(1L, 1L) } returns AttachAddOnError.PaymentFailed("Declined").left()
            val response = controller.attachAddOn(1L, AttachAddOnRequest(addonId = 1L))
            response.statusCode shouldBe HttpStatus.BAD_GATEWAY
        }
    }

    describe("Detach Add-on API") {
        // API-DA1
        it("returns 200 on successful detachment") {
            every { detachAddOnUseCase.execute(1L, 1L) } returns subscriptionAddOn().copy(
                status = SubscriptionAddOnStatus.Detached(now), detachedAt = now,
            ).right()
            val response = controller.detachAddOn(1L, 1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-DA2
        it("returns 404 when add-on not attached") {
            every { detachAddOnUseCase.execute(1L, 1L) } returns DetachAddOnError.AddOnNotAttached.left()
            val response = controller.detachAddOn(1L, 1L)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    describe("Update Seat Count API") {
        // API-ST1
        it("returns 200 on seat increase") {
            every { updateSeatCountUseCase.execute(1L, 10) } returns subscription().copy(seatCount = 10).right()
            val response = controller.updateSeatCount(1L, UpdateSeatCountRequest(seatCount = 10))
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-ST2
        it("returns 200 on seat decrease") {
            every { updateSeatCountUseCase.execute(1L, 3) } returns subscription().copy(seatCount = 3).right()
            val response = controller.updateSeatCount(1L, UpdateSeatCountRequest(seatCount = 3))
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-ST3
        it("returns 409 when not per-seat plan") {
            every { updateSeatCountUseCase.execute(1L, 5) } returns UpdateSeatCountError.NotPerSeatPlan.left()
            val response = controller.updateSeatCount(1L, UpdateSeatCountRequest(seatCount = 5))
            response.statusCode shouldBe HttpStatus.CONFLICT
        }

        // API-ST4
        it("returns 502 on payment failure") {
            every { updateSeatCountUseCase.execute(1L, 10) } returns UpdateSeatCountError.PaymentFailed("Declined").left()
            val response = controller.updateSeatCount(1L, UpdateSeatCountRequest(seatCount = 10))
            response.statusCode shouldBe HttpStatus.BAD_GATEWAY
        }

        // API-ST5
        it("returns 400 for invalid seat count") {
            every { updateSeatCountUseCase.execute(1L, 0) } returns UpdateSeatCountError.InvalidInput("seatCount", "Must be positive").left()
            val response = controller.updateSeatCount(1L, UpdateSeatCountRequest(seatCount = 0))
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        // API-ST6
        it("returns 409 for above maximum") {
            every { updateSeatCountUseCase.execute(1L, 999) } returns UpdateSeatCountError.AboveMaximum.left()
            val response = controller.updateSeatCount(1L, UpdateSeatCountRequest(seatCount = 999))
            response.statusCode shouldBe HttpStatus.CONFLICT
        }
    }

    describe("List Add-ons API") {
        // API-LA1
        it("returns 200 with list of add-ons") {
            val dto = SubscriptionAddOnDto(
                id = 1L, subscriptionId = 1L, addOnId = 1L, addOnName = "Support",
                addOnPriceAmount = "9.99", addOnPriceCurrency = "USD", addOnBillingType = "FLAT",
                quantity = 1, status = "ACTIVE", attachedAt = now, detachedAt = null,
            )
            every { subscriptionAddOnListQueryUseCase.execute(1L) } returns listOf(dto).right()
            val response = controller.listAddOns(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-LA2
        it("returns 200 with empty list") {
            every { subscriptionAddOnListQueryUseCase.execute(1L) } returns emptyList<SubscriptionAddOnDto>().right()
            val response = controller.listAddOns(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-LA3
        it("returns 400 for invalid subscription ID") {
            every { subscriptionAddOnListQueryUseCase.execute(-1L) } returns SubscriptionAddOnListQueryError.InvalidInput("subscriptionId", "Must be positive").left()
            val response = controller.listAddOns(-1L)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }
})
