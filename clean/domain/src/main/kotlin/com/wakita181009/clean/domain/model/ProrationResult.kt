package com.wakita181009.clean.domain.model

data class ProrationResult(
    val credit: InvoiceLineItem,
    val charge: InvoiceLineItem,
    val netAmount: Money,
)
