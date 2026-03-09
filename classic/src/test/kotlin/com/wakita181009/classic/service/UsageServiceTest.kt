package com.wakita181009.classic.service

import com.wakita181009.classic.dto.RecordUsageRequest
import com.wakita181009.classic.exception.BusinessRuleViolationException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.model.UsageRecord
import com.wakita181009.classic.repository.SubscriptionRepository
import com.wakita181009.classic.repository.UsageRecordRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class UsageServiceTest {
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val usageRecordRepository = mockk<UsageRecordRepository>()
    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: UsageService

    @BeforeEach
    fun setUp() {
        service = UsageService(subscriptionRepository, usageRecordRepository, clock)
    }

    private fun samplePlan(usageLimit: Int? = 10000) =
        Plan(
            id = 1L,
            name = "Pro",
            billingInterval = BillingInterval.MONTHLY,
            basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
            tier = PlanTier.PROFESSIONAL,
            active = true,
            usageLimit = usageLimit,
            features = setOf("feature1"),
        )

    private fun sampleSubscription(
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        plan: Plan = samplePlan(),
    ) = Subscription(
        id = 1L,
        customerId = 1L,
        plan = plan,
        status = status,
        currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
        currentPeriodEnd = Instant.parse("2025-01-31T00:00:00Z"),
    )

    // US-H1: Record API usage
    @Test
    fun `records usage for active subscription`() {
        val sub = sampleSubscription()
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        every { usageRecordRepository.findByIdempotencyKey("req-1") } returns null
        every { usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(any(), any(), any()) } returns
            emptyList()
        every { usageRecordRepository.save(any()) } answers { firstArg() }

        val request = RecordUsageRequest(metricName = "api_calls", quantity = 100, idempotencyKey = "req-1")
        val result = service.recordUsage(1L, request)
        assertEquals(100, result.quantity)
        verify(exactly = 1) { usageRecordRepository.save(any()) }
    }

    // US-H2: Record at limit boundary
    @Test
    fun `records usage at exact limit boundary`() {
        val sub = sampleSubscription(plan = samplePlan(usageLimit = 1000))
        val existing =
            listOf(
                UsageRecord(
                    id = 1L,
                    subscription = sub,
                    metricName = "api_calls",
                    quantity = 900,
                    recordedAt = fixedInstant,
                    idempotencyKey = "old",
                ),
            )
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        every { usageRecordRepository.findByIdempotencyKey("req-2") } returns null
        every { usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(any(), any(), any()) } returns
            existing
        every { usageRecordRepository.save(any()) } answers { firstArg() }

        val request = RecordUsageRequest(metricName = "api_calls", quantity = 100, idempotencyKey = "req-2")
        val result = service.recordUsage(1L, request)
        assertEquals(100, result.quantity)
    }

    // US-H3: Idempotent duplicate
    @Test
    fun `returns existing record for duplicate idempotency key`() {
        val sub = sampleSubscription()
        val existingRecord =
            UsageRecord(
                id = 99L,
                subscription = sub,
                metricName = "api_calls",
                quantity = 100,
                recordedAt = fixedInstant,
                idempotencyKey = "req-1",
            )
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        every { usageRecordRepository.findByIdempotencyKey("req-1") } returns existingRecord

        val request = RecordUsageRequest(metricName = "api_calls", quantity = 100, idempotencyKey = "req-1")
        val result = service.recordUsage(1L, request)
        assertEquals(99L, result.id)
        verify(exactly = 0) { usageRecordRepository.save(any()) }
    }

    // US-H4: Unlimited plan
    @Test
    fun `allows large usage on unlimited plan`() {
        val sub = sampleSubscription(plan = samplePlan(usageLimit = null))
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        every { usageRecordRepository.findByIdempotencyKey("req-1") } returns null
        every { usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(any(), any(), any()) } returns
            emptyList()
        every { usageRecordRepository.save(any()) } answers { firstArg() }

        val request = RecordUsageRequest(metricName = "api_calls", quantity = 999999, idempotencyKey = "req-1")
        val result = service.recordUsage(1L, request)
        assertEquals(999999, result.quantity)
    }

    // US-B1: Subscription not found
    @Test
    fun `throws SubscriptionNotFoundException when not found`() {
        every { subscriptionRepository.findByIdWithPlan(999L) } returns null
        val request = RecordUsageRequest(metricName = "api_calls", quantity = 100, idempotencyKey = "req-1")
        assertThrows(SubscriptionNotFoundException::class.java) { service.recordUsage(999L, request) }
    }

    // US-B2: Not Active (Paused)
    @Test
    fun `throws when subscription is Paused`() {
        val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        val request = RecordUsageRequest(metricName = "api_calls", quantity = 100, idempotencyKey = "req-1")
        assertThrows(InvalidStateTransitionException::class.java) { service.recordUsage(1L, request) }
    }

    // US-B3: Not Active (Trial)
    @Test
    fun `throws when subscription is in Trial`() {
        val sub = sampleSubscription(status = SubscriptionStatus.TRIAL)
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        val request = RecordUsageRequest(metricName = "api_calls", quantity = 100, idempotencyKey = "req-1")
        assertThrows(InvalidStateTransitionException::class.java) { service.recordUsage(1L, request) }
    }

    // US-B5: Usage limit exceeded
    @Test
    fun `throws when usage limit exceeded`() {
        val sub = sampleSubscription(plan = samplePlan(usageLimit = 1000))
        val existing =
            listOf(
                UsageRecord(
                    id = 1L,
                    subscription = sub,
                    metricName = "api_calls",
                    quantity = 950,
                    recordedAt = fixedInstant,
                    idempotencyKey = "old",
                ),
            )
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        every { usageRecordRepository.findByIdempotencyKey("req-2") } returns null
        every { usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(any(), any(), any()) } returns
            existing

        val request = RecordUsageRequest(metricName = "api_calls", quantity = 51, idempotencyKey = "req-2")
        assertThrows(BusinessRuleViolationException::class.java) { service.recordUsage(1L, request) }
    }

    // US-B6: Already at limit
    @Test
    fun `throws when already at usage limit`() {
        val sub = sampleSubscription(plan = samplePlan(usageLimit = 1000))
        val existing =
            listOf(
                UsageRecord(
                    id = 1L,
                    subscription = sub,
                    metricName = "api_calls",
                    quantity = 1000,
                    recordedAt = fixedInstant,
                    idempotencyKey = "old",
                ),
            )
        every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
        every { usageRecordRepository.findByIdempotencyKey("req-2") } returns null
        every { usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(any(), any(), any()) } returns
            existing

        val request = RecordUsageRequest(metricName = "api_calls", quantity = 1, idempotencyKey = "req-2")
        assertThrows(BusinessRuleViolationException::class.java) { service.recordUsage(1L, request) }
    }
}
