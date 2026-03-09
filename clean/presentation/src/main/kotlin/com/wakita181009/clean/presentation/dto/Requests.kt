package com.wakita181009.clean.presentation.dto

data class CreateSubscriptionRequest(
    val customerId: Long,
    val planId: Long,
    val paymentMethod: String,
    val discountCode: String?,
    val seatCount: Int? = null,
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

data class AttachAddOnRequest(
    val addonId: Long,
)

data class UpdateSeatCountRequest(
    val seatCount: Int,
)

data class IssueCreditNoteRequest(
    val type: String,
    val application: String,
    val amount: java.math.BigDecimal? = null,
    val reason: String,
)
