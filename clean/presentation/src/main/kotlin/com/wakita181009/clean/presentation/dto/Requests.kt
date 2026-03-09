package com.wakita181009.clean.presentation.dto

data class CreateSubscriptionRequest(
    val customerId: Long,
    val planId: Long,
    val paymentMethod: String,
    val discountCode: String?,
)

data class ChangePlanRequest(
    val newPlanId: Long,
)

data class CancelSubscriptionRequest(
    val immediate: Boolean = false,
)

data class RecordUsageRequest(
    val metricName: String,
    val quantity: Int,
    val idempotencyKey: String,
)
