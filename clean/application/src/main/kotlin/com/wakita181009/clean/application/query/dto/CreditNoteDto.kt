package com.wakita181009.clean.application.query.dto

import java.time.Instant

data class CreditNoteDto(
    val id: Long,
    val invoiceId: Long,
    val subscriptionId: Long,
    val amount: String,
    val currency: String,
    val reason: String,
    val type: String,
    val application: String,
    val status: String,
    val refundTransactionId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
