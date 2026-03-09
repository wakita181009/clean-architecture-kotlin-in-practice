package com.wakita181009.clean.application.command.dto

sealed interface PaymentError {
    val reason: String

    data class Declined(override val reason: String = "Payment declined") : PaymentError
    data class Timeout(override val reason: String = "Payment gateway timeout") : PaymentError
    data class InsufficientFunds(override val reason: String = "Insufficient funds") : PaymentError
    data class Unknown(override val reason: String = "Unknown payment error") : PaymentError
}
