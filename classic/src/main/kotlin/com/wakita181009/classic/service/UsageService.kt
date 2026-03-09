package com.wakita181009.classic.service

import com.wakita181009.classic.dto.RecordUsageRequest
import com.wakita181009.classic.exception.BusinessRuleViolationException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.model.UsageRecord
import com.wakita181009.classic.repository.SubscriptionRepository
import com.wakita181009.classic.repository.UsageRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class UsageService(
    private val subscriptionRepository: SubscriptionRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val clock: Clock,
) {
    @Transactional
    fun recordUsage(
        subscriptionId: Long,
        request: RecordUsageRequest,
    ): UsageRecord {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status != SubscriptionStatus.ACTIVE) {
            throw InvalidStateTransitionException(subscription.status.name, "record usage (requires ACTIVE)")
        }

        // Idempotency check
        val existing = usageRecordRepository.findByIdempotencyKey(request.idempotencyKey)
        if (existing != null) {
            return existing
        }

        // Check usage limit
        val plan = subscription.plan
        plan.usageLimit?.let { limit ->
            val currentUsage =
                usageRecordRepository
                    .findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                        subscriptionId,
                        subscription.currentPeriodStart,
                        subscription.currentPeriodEnd,
                    ).sumOf { it.quantity }

            if (currentUsage + request.quantity > limit) {
                throw BusinessRuleViolationException(
                    "Usage limit exceeded: current=$currentUsage + requested=${request.quantity} > limit=$limit",
                )
            }
        }

        val usageRecord =
            UsageRecord(
                subscription = subscription,
                metricName = request.metricName,
                quantity = request.quantity,
                recordedAt = now,
                idempotencyKey = request.idempotencyKey,
            )

        return usageRecordRepository.save(usageRecord)
    }
}
