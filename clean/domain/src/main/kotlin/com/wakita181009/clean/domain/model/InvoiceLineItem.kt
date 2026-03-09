package com.wakita181009.clean.domain.model

data class InvoiceLineItem(
    val description: String,
    val amount: Money,
    val type: InvoiceLineItemType,
)
