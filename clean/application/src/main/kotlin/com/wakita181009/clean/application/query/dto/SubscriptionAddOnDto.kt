package com.wakita181009.clean.application.query.dto

import java.time.Instant

data class SubscriptionAddOnDto(
    val id: Long,
    val subscriptionId: Long,
    val addOnId: Long,
    val addOnName: String,
    val addOnPriceAmount: String,
    val addOnPriceCurrency: String,
    val addOnBillingType: String,
    val quantity: Int,
    val status: String,
    val attachedAt: Instant,
    val detachedAt: Instant?,
)
