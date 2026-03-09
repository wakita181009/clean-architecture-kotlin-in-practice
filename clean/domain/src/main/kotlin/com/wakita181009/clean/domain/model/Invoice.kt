package com.wakita181009.clean.domain.model

import java.time.Instant
import java.time.LocalDate

data class Invoice(
    val id: InvoiceId?,
    val subscriptionId: SubscriptionId,
    val lineItems: List<InvoiceLineItem>,
    val subtotal: Money,
    val discountAmount: Money,
    val total: Money,
    val currency: Currency,
    val status: InvoiceStatus,
    val dueDate: LocalDate,
    val paidAt: Instant?,
    val paymentAttemptCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
