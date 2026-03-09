package com.wakita181009.clean.domain.error

sealed interface InvoiceError : DomainError {
    data class InvalidTransition(val from: String, val to: String) : InvoiceError {
        override val message: String = "Invalid invoice transition from $from to $to"
    }

    data object EmptyLineItems : InvoiceError {
        override val message: String = "Invoice must have at least one line item"
    }
}
