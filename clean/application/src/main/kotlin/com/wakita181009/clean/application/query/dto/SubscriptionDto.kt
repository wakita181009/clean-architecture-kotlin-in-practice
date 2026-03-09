package com.wakita181009.clean.application.query.dto

import java.time.Instant

data class SubscriptionDto(
    val id: Long,
    val customerId: Long,
    val planId: Long,
    val planName: String,
    val planTier: String,
    val planBillingInterval: String,
    val planBasePriceAmount: String,
    val planBasePriceCurrency: String,
    val status: String,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant,
    val trialEnd: Instant?,
    val pausedAt: Instant?,
    val canceledAt: Instant?,
    val cancelAtPeriodEnd: Boolean,
    val discountType: String?,
    val discountValue: String?,
    val discountRemainingCycles: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val seatCount: Int? = null,
    val accountCreditBalanceAmount: String = "0",
    val accountCreditBalanceCurrency: String = "USD",
)
