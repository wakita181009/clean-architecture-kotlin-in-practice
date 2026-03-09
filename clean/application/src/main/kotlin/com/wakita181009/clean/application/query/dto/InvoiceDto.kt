package com.wakita181009.clean.application.query.dto

import java.time.Instant
import java.time.LocalDate

data class InvoiceDto(
    val id: Long,
    val subscriptionId: Long,
    val lineItems: List<InvoiceLineItemDto>,
    val subtotalAmount: String,
    val subtotalCurrency: String,
    val discountAmount: String,
    val totalAmount: String,
    val totalCurrency: String,
    val status: String,
    val dueDate: LocalDate,
    val paidAt: Instant?,
    val paymentAttemptCount: Int,
    val createdAt: Instant,
)
