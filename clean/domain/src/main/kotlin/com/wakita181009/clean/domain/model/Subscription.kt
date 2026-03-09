package com.wakita181009.clean.domain.model

import java.time.Instant

data class Subscription(
    val id: SubscriptionId?,
    val customerId: CustomerId,
    val plan: Plan,
    val status: SubscriptionStatus,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant,
    val trialStart: Instant?,
    val trialEnd: Instant?,
    val pausedAt: Instant?,
    val canceledAt: Instant?,
    val cancelAtPeriodEnd: Boolean,
    val gracePeriodEnd: Instant?,
    val pauseCountInPeriod: Int,
    val paymentMethod: PaymentMethod?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
