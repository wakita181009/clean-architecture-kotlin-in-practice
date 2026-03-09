package com.wakita181009.clean.application.command.dto

import java.time.Instant

data class RefundResult(
    val refundTransactionId: String,
    val processedAt: Instant,
)
