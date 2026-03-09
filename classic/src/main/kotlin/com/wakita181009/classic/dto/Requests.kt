package com.wakita181009.classic.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CreateSubscriptionRequest(
    @field:Positive(message = "customerId must be positive")
    val customerId: Long,
    @field:Positive(message = "planId must be positive")
    val planId: Long,
    @field:NotBlank(message = "paymentMethod is required")
    val paymentMethod: String,
    val discountCode: String? = null,
)

data class ChangePlanRequest(
    @field:Positive(message = "newPlanId must be positive")
    val newPlanId: Long,
)

data class CancelSubscriptionRequest(
    val immediate: Boolean = false,
)

data class RecordUsageRequest(
    @field:NotBlank(message = "metricName is required")
    val metricName: String,
    @field:Positive(message = "quantity must be positive")
    val quantity: Int,
    @field:NotBlank(message = "idempotencyKey is required")
    val idempotencyKey: String,
)
