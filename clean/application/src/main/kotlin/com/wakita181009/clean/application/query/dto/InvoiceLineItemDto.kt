package com.wakita181009.clean.application.query.dto

data class InvoiceLineItemDto(
    val description: String,
    val amount: String,
    val currency: String,
    val type: String,
)
