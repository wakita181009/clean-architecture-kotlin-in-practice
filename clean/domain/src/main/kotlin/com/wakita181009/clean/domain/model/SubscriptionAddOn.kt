package com.wakita181009.clean.domain.model

import java.time.Instant

data class SubscriptionAddOn(
    val id: SubscriptionAddOnId?,
    val subscriptionId: SubscriptionId,
    val addOnId: AddOnId,
    val quantity: Int,
    val status: SubscriptionAddOnStatus,
    val attachedAt: Instant,
    val detachedAt: Instant?,
)
