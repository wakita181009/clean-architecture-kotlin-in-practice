package com.wakita181009.classic.controller

import com.ninjasquad.springmockk.MockkBean
import com.wakita181009.classic.exception.DuplicateSubscriptionException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.model.UsageRecord
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@WebMvcTest(SubscriptionController::class)
class SubscriptionControllerTest {
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

    private fun samplePlan() =
        Plan(
            id = 1L,
            name = "Professional",
            billingInterval = BillingInterval.MONTHLY,
            basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
            tier = PlanTier.PROFESSIONAL,
            active = true,
            features = setOf("feature1"),
        )

    private fun sampleSubscription(status: SubscriptionStatus = SubscriptionStatus.TRIAL) =
        Subscription(
            id = 1L,
            customerId = 1L,
            plan = samplePlan(),
            status = status,
            currentPeriodStart = fixedInstant,
            currentPeriodEnd = fixedInstant.plus(Duration.ofDays(14)),
            trialStart = fixedInstant,
            trialEnd = fixedInstant.plus(Duration.ofDays(14)),
        )

    // API-CS1: Successful creation
    @Test
    fun `POST creates subscription and returns 201`() {
        every { subscriptionService.createSubscription(any()) } returns sampleSubscription()

        mockMvc
            .perform(
                post("/api/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"customerId":1,"planId":1,"paymentMethod":"CREDIT_CARD"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("TRIAL"))
    }

    // API-CS2: Validation failure
    @Test
    fun `POST returns 400 for invalid customerId`() {
        mockMvc
            .perform(
                post("/api/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"customerId":0,"planId":1,"paymentMethod":"CREDIT_CARD"}"""),
            ).andExpect(status().isBadRequest)
    }

    // API-CS3: Duplicate subscription
    @Test
    fun `POST returns 409 for duplicate subscription`() {
        every { subscriptionService.createSubscription(any()) } throws DuplicateSubscriptionException(1L)

        mockMvc
            .perform(
                post("/api/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"customerId":1,"planId":1,"paymentMethod":"CREDIT_CARD"}"""),
            ).andExpect(status().isConflict)
    }

    // API-CP1: Successful upgrade
    @Test
    fun `PUT plan returns 200 on successful upgrade`() {
        every { subscriptionService.changePlan(1L, 2L) } returns sampleSubscription(status = SubscriptionStatus.ACTIVE)

        mockMvc
            .perform(
                put("/api/subscriptions/1/plan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"newPlanId":2}"""),
            ).andExpect(status().isOk)
    }

    // API-CP3: Invalid state
    @Test
    fun `PUT plan returns 409 on invalid state`() {
        every { subscriptionService.changePlan(1L, 2L) } throws InvalidStateTransitionException("PAUSED", "plan change")

        mockMvc
            .perform(
                put("/api/subscriptions/1/plan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"newPlanId":2}"""),
            ).andExpect(status().isConflict)
    }

    // API-PA1: Successful pause
    @Test
    fun `POST pause returns 200`() {
        every { subscriptionService.pauseSubscription(1L) } returns sampleSubscription(status = SubscriptionStatus.PAUSED)

        mockMvc
            .perform(post("/api/subscriptions/1/pause"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAUSED"))
    }

    // API-RE1: Successful resume
    @Test
    fun `POST resume returns 200`() {
        every { subscriptionService.resumeSubscription(1L) } returns sampleSubscription(status = SubscriptionStatus.ACTIVE)

        mockMvc
            .perform(post("/api/subscriptions/1/resume"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    // API-CA1: End-of-period cancel
    @Test
    fun `POST cancel with immediate false returns 200`() {
        val sub = sampleSubscription(status = SubscriptionStatus.ACTIVE)
        every { subscriptionService.cancelSubscription(1L, false) } returns sub

        mockMvc
            .perform(
                post("/api/subscriptions/1/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"immediate":false}"""),
            ).andExpect(status().isOk)
    }

    // API-CA2: Immediate cancel
    @Test
    fun `POST cancel with immediate true returns 200`() {
        every { subscriptionService.cancelSubscription(1L, true) } returns sampleSubscription(status = SubscriptionStatus.CANCELED)

        mockMvc
            .perform(
                post("/api/subscriptions/1/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"immediate":true}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
    }

    // API-US1: Record usage
    @Test
    fun `POST usage returns 201`() {
        val sub = sampleSubscription(status = SubscriptionStatus.ACTIVE)
        val usage =
            UsageRecord(
                id = 1L,
                subscription = sub,
                metricName = "api_calls",
                quantity = 150,
                recordedAt = fixedInstant,
                idempotencyKey = "req-abc-123",
            )
        every { usageService.recordUsage(1L, any()) } returns usage

        mockMvc
            .perform(
                post("/api/subscriptions/1/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"metricName":"api_calls","quantity":150,"idempotencyKey":"req-abc-123"}"""),
            ).andExpect(status().isCreated)
    }

    // API-G1: Get subscription
    @Test
    fun `GET subscription returns 200`() {
        every { subscriptionService.getSubscription(1L) } returns sampleSubscription()

        mockMvc
            .perform(get("/api/subscriptions/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
    }

    // API-G2: Subscription not found
    @Test
    fun `GET subscription returns 404 when not found`() {
        every { subscriptionService.getSubscription(99999L) } throws SubscriptionNotFoundException(99999L)

        mockMvc
            .perform(get("/api/subscriptions/99999"))
            .andExpect(status().isNotFound)
    }

    // API-G3: List by customer
    @Test
    fun `GET subscriptions by customerId returns 200`() {
        every { subscriptionService.listByCustomerId(1L) } returns listOf(sampleSubscription())

        mockMvc
            .perform(get("/api/subscriptions").param("customerId", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
    }

    // API-G5: Invalid ID
    @Test
    fun `GET subscription with negative ID returns 400`() {
        mockMvc
            .perform(get("/api/subscriptions/-1"))
            .andExpect(status().isBadRequest)
    }
}
