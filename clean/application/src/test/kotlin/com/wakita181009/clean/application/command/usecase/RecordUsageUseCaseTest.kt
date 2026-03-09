package com.wakita181009.clean.application.command.usecase

import arrow.core.right
import com.wakita181009.clean.application.command.dto.RecordUsageCommand
import com.wakita181009.clean.application.command.error.RecordUsageError
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.command.port.UsageQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
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
import com.wakita181009.clean.domain.repository.UsageRecordRepository
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

class RecordUsageUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val usageQueryPort = mockk<UsageQueryPort>()
    val usageRecordRepository = mockk<UsageRecordRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = RecordUsageUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        usageQueryPort = usageQueryPort,
        usageRecordRepository = usageRecordRepository,
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
        usageLimit = 10000,
        features = setOf("api"),
        tier = PlanTier.PROFESSIONAL,
        active = true,
    ).getOrNull()!!

    fun activeSub() = Subscription(
        id = subId,
        customerId = CustomerId(1L).getOrNull()!!,
        plan = plan,
        status = SubscriptionStatus.Active,
        currentPeriodStart = now.minus(Duration.ofDays(10)),
        currentPeriodEnd = now.plus(Duration.ofDays(20)),
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = null, createdAt = now, updatedAt = now,
    )

    beforeTest {
        clearMocks(subscriptionCommandQueryPort, usageQueryPort, usageRecordRepository, clockPort, transactionPort)
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
    }

    describe("Happy path") {
        // US-H1
        it("records API usage") {
            every { usageQueryPort.findByIdempotencyKey(any()) } returns (null as UsageRecord?).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { usageQueryPort.sumQuantityForPeriod(any(), any(), any()) } returns 0L.right()
            every { usageRecordRepository.save(any()) } answers {
                val record = firstArg<UsageRecord>()
                record.copy(id = UsageId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api_calls", quantity = 100, idempotencyKey = "req-1"),
            ).shouldBeRight()
            result.quantity shouldBe 100
            result.metricName shouldBe MetricName("api_calls").getOrNull()!!
        }

        // US-H2
        it("records at limit boundary") {
            every { usageQueryPort.findByIdempotencyKey(any()) } returns (null as UsageRecord?).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { usageQueryPort.sumQuantityForPeriod(any(), any(), any()) } returns 9900L.right()
            every { usageRecordRepository.save(any()) } answers {
                firstArg<UsageRecord>().copy(id = UsageId(1L).getOrNull()!!).right()
            }

            useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api_calls", quantity = 100, idempotencyKey = "req-2"),
            ).shouldBeRight()
        }

        // US-H3
        it("returns existing record for duplicate idempotency key") {
            val existingRecord = UsageRecord(
                id = UsageId(1L).getOrNull()!!,
                subscriptionId = subId,
                metricName = MetricName("api_calls").getOrNull()!!,
                quantity = 100,
                recordedAt = now,
                idempotencyKey = IdempotencyKey("req-1").getOrNull()!!,
            )
            every { usageQueryPort.findByIdempotencyKey(any()) } returns existingRecord.right()

            val result = useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api_calls", quantity = 100, idempotencyKey = "req-1"),
            ).shouldBeRight()
            result.id shouldBe existingRecord.id
        }

        // US-H4
        it("allows unlimited usage when plan has no limit") {
            val unlimitedPlan = plan.copy(usageLimit = null)
            val sub = activeSub().copy(plan = unlimitedPlan)
            every { usageQueryPort.findByIdempotencyKey(any()) } returns (null as UsageRecord?).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { usageRecordRepository.save(any()) } answers {
                firstArg<UsageRecord>().copy(id = UsageId(1L).getOrNull()!!).right()
            }

            useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api_calls", quantity = 999999, idempotencyKey = "req-3"),
            ).shouldBeRight()
        }
    }

    describe("Validation errors") {
        // US-V1
        it("rejects invalid subscription ID") {
            useCase.execute(
                RecordUsageCommand(subscriptionId = 0L, metricName = "api", quantity = 1, idempotencyKey = "k"),
            ).shouldBeLeft().shouldBeInstanceOf<RecordUsageError.InvalidInput>()
        }

        // US-V2
        it("rejects zero quantity") {
            useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api", quantity = 0, idempotencyKey = "k"),
            ).shouldBeLeft().shouldBeInstanceOf<RecordUsageError.InvalidInput>()
        }

        // US-V3
        it("rejects negative quantity") {
            useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api", quantity = -1, idempotencyKey = "k"),
            ).shouldBeLeft().shouldBeInstanceOf<RecordUsageError.InvalidInput>()
        }

        // US-V4
        it("rejects blank metric name") {
            useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "", quantity = 1, idempotencyKey = "k"),
            ).shouldBeLeft().shouldBeInstanceOf<RecordUsageError.InvalidInput>()
        }

        // US-V5
        it("rejects blank idempotency key") {
            useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api", quantity = 1, idempotencyKey = ""),
            ).shouldBeLeft().shouldBeInstanceOf<RecordUsageError.InvalidInput>()
        }
    }

    describe("Business rule errors") {
        // US-B5
        it("rejects when usage limit exceeded") {
            every { usageQueryPort.findByIdempotencyKey(any()) } returns (null as UsageRecord?).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns activeSub().right()
            every { usageQueryPort.sumQuantityForPeriod(any(), any(), any()) } returns 9950L.right()

            val result = useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api", quantity = 51, idempotencyKey = "k"),
            ).shouldBeLeft()
            result.shouldBeInstanceOf<RecordUsageError.UsageLimitExceeded>()
        }

        // US-B2
        it("rejects non-Active subscription (Paused)") {
            val pausedSub = activeSub().copy(status = SubscriptionStatus.Paused(now))
            every { usageQueryPort.findByIdempotencyKey(any()) } returns (null as UsageRecord?).right()
            every { subscriptionCommandQueryPort.findById(subId) } returns pausedSub.right()

            useCase.execute(
                RecordUsageCommand(subscriptionId = 1L, metricName = "api", quantity = 1, idempotencyKey = "k"),
            ).shouldBeLeft().shouldBeInstanceOf<RecordUsageError.NotActive>()
        }
    }
})
