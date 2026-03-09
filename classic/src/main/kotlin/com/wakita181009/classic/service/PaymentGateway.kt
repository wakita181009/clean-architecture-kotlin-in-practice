package com.wakita181009.classic.service

import com.wakita181009.classic.model.Money
import java.time.Instant

interface PaymentGateway {
    fun charge(
        amount: Money,
        paymentMethod: String,
        customerRef: Long,
    ): PaymentResult

    fun refund(
        transactionId: String,
        amount: Money,
    ): RefundResult
}

data class PaymentResult(
    val success: Boolean,
    val transactionId: String? = null,
    val processedAt: Instant? = null,
    val errorReason: String? = null,
)

data class RefundResult(
    val success: Boolean,
    val errorReason: String? = null,
)
