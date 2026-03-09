package com.wakita181009.classic.controller

import com.ninjasquad.springmockk.MockkBean
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.NotPerSeatPlanException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.exception.SeatCountOutOfRangeException
import com.wakita181009.classic.exception.SubscriptionAddOnNotFoundException
import com.wakita181009.classic.exception.TierIncompatibilityException
import com.wakita181009.classic.model.AddOn
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.BillingType
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionAddOn
import com.wakita181009.classic.model.SubscriptionAddOnStatus
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.service.AddOnService
import com.wakita181009.classic.service.SeatService
import com.wakita181009.classic.service.SubscriptionService
import com.wakita181009.classic.service.UsageService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@WebMvcTest(SubscriptionController::class)
class SubscriptionControllerPhase1Test {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var subscriptionService: SubscriptionService

    @MockkBean
    lateinit var usageService: UsageService

    @MockkBean
    lateinit var addOnService: AddOnService

    @MockkBean
    lateinit var seatService: SeatService

    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")

    private fun sampleSubscription(
        seatCount: Int? = null,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    ) = Subscription(
        id = 1L,
        customerId = 1L,
        plan =
            Plan(
                id = 1L,
                name = "Team",
                billingInterval = BillingInterval.MONTHLY,
                basePrice = Money(BigDecimal("10.00"), Money.Currency.USD),
                tier = PlanTier.PROFESSIONAL,
                features = setOf("feature1"),
                perSeatPricing = true,
                minimumSeats = 1,
                maximumSeats = 100,
            ),
        status = status,
        currentPeriodStart = fixedInstant,
        currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
        seatCount = seatCount,
        accountCreditBalance = Money.zero(Money.Currency.USD),
    )

    private fun sampleAddOn() =
        AddOn(
            id = 10L,
            name = "Support",
            price = Money(BigDecimal("9.99"), Money.Currency.USD),
            billingType = BillingType.FLAT,
            compatibleTiers = setOf(PlanTier.PROFESSIONAL),
        )

    private fun sampleSubscriptionAddOn(
        sub: Subscription = sampleSubscription(),
        addOn: AddOn = sampleAddOn(),
        status: SubscriptionAddOnStatus = SubscriptionAddOnStatus.ACTIVE,
    ) = SubscriptionAddOn(
        id = 1L,
        subscription = sub,
        addOn = addOn,
        quantity = 1,
        status = status,
        attachedAt = fixedInstant,
    )

    // API-AO1: Successful attachment
    @Test
    fun `POST addons returns 201 on success`() {
        every { addOnService.attachAddOn(1L, 10L) } returns sampleSubscriptionAddOn()

        mockMvc
            .perform(
                post("/api/subscriptions/1/addons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"addonId":10}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    // API-AO2: Subscription not Active
    @Test
    fun `POST addons returns 409 when subscription not active`() {
        every { addOnService.attachAddOn(1L, 10L) } throws InvalidStateTransitionException("PAUSED", "attach add-on")

        mockMvc
            .perform(
                post("/api/subscriptions/1/addons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"addonId":10}"""),
            ).andExpect(status().isConflict)
    }

    // API-AO3: Tier incompatible
    @Test
    fun `POST addons returns 409 on tier incompatibility`() {
        every { addOnService.attachAddOn(1L, 10L) } throws TierIncompatibilityException("STARTER", "PROFESSIONAL, ENTERPRISE")

        mockMvc
            .perform(
                post("/api/subscriptions/1/addons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"addonId":10}"""),
            ).andExpect(status().isConflict)
    }

    // API-AO4: Payment failure
    @Test
    fun `POST addons returns 502 on payment failure`() {
        every { addOnService.attachAddOn(1L, 10L) } throws PaymentFailedException("Gateway error")

        mockMvc
            .perform(
                post("/api/subscriptions/1/addons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"addonId":10}"""),
            ).andExpect(status().isBadGateway)
    }

    // API-DA1: Successful detachment
    @Test
    fun `DELETE addons returns 200 on successful detachment`() {
        val sa = sampleSubscriptionAddOn(status = SubscriptionAddOnStatus.DETACHED)
        every { addOnService.detachAddOn(1L, 10L) } returns sa

        mockMvc
            .perform(delete("/api/subscriptions/1/addons/10"))
            .andExpect(status().isOk)
    }

    // API-DA2: Add-on not attached
    @Test
    fun `DELETE addons returns 404 when not attached`() {
        every { addOnService.detachAddOn(1L, 999L) } throws SubscriptionAddOnNotFoundException(1L, 999L)

        mockMvc
            .perform(delete("/api/subscriptions/1/addons/999"))
            .andExpect(status().isNotFound)
    }

    // API-ST1: Increase seats
    @Test
    fun `PUT seats returns 200 on increase`() {
        val sub = sampleSubscription(seatCount = 10)
        every { seatService.updateSeatCount(1L, 10) } returns sub

        mockMvc
            .perform(
                put("/api/subscriptions/1/seats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"seatCount":10}"""),
            ).andExpect(status().isOk)
    }

    // API-ST2: Decrease seats
    @Test
    fun `PUT seats returns 200 on decrease`() {
        val sub = sampleSubscription(seatCount = 3)
        every { seatService.updateSeatCount(1L, 3) } returns sub

        mockMvc
            .perform(
                put("/api/subscriptions/1/seats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"seatCount":3}"""),
            ).andExpect(status().isOk)
    }

    // API-ST3: Not per-seat plan
    @Test
    fun `PUT seats returns 409 when not per-seat plan`() {
        every { seatService.updateSeatCount(1L, 10) } throws NotPerSeatPlanException()

        mockMvc
            .perform(
                put("/api/subscriptions/1/seats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"seatCount":10}"""),
            ).andExpect(status().isConflict)
    }

    // API-ST4: Payment failure on increase
    @Test
    fun `PUT seats returns 502 on payment failure`() {
        every { seatService.updateSeatCount(1L, 10) } throws PaymentFailedException("Gateway error")

        mockMvc
            .perform(
                put("/api/subscriptions/1/seats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"seatCount":10}"""),
            ).andExpect(status().isBadGateway)
    }

    // API-ST5: Below minimum (validation)
    @Test
    fun `PUT seats returns 400 when seatCount is zero`() {
        mockMvc
            .perform(
                put("/api/subscriptions/1/seats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"seatCount":0}"""),
            ).andExpect(status().isBadRequest)
    }

    // API-ST6: Above maximum
    @Test
    fun `PUT seats returns 409 when above maximum`() {
        every { seatService.updateSeatCount(1L, 999) } throws SeatCountOutOfRangeException("Seat count 999 exceeds maximum 10")

        mockMvc
            .perform(
                put("/api/subscriptions/1/seats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"seatCount":999}"""),
            ).andExpect(status().isConflict)
    }

    // API-LA1: List subscription add-ons
    @Test
    fun `GET addons returns 200 with list`() {
        every { addOnService.listAddOns(1L) } returns listOf(sampleSubscriptionAddOn())

        mockMvc
            .perform(get("/api/subscriptions/1/addons"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
    }

    // API-LA2: Empty list
    @Test
    fun `GET addons returns 200 with empty list`() {
        every { addOnService.listAddOns(1L) } returns emptyList()

        mockMvc
            .perform(get("/api/subscriptions/1/addons"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // API-LA3: Invalid subscription ID
    @Test
    fun `GET addons returns 400 for negative subscription ID`() {
        mockMvc
            .perform(get("/api/subscriptions/-1/addons"))
            .andExpect(status().isBadRequest)
    }
}
