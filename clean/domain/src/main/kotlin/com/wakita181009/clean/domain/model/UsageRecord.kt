package com.wakita181009.clean.domain.model

import java.time.Instant

data class UsageRecord(
    val id: UsageId?,
    val subscriptionId: SubscriptionId,
    val metricName: MetricName,
    val quantity: Int,
    val recordedAt: Instant,
    val idempotencyKey: IdempotencyKey,
)
