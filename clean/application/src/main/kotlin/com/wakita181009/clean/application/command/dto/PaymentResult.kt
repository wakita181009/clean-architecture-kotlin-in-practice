package com.wakita181009.clean.application.command.dto

import java.time.Instant

data class PaymentResult(
    val transactionId: String,
    val processedAt: Instant,
)
