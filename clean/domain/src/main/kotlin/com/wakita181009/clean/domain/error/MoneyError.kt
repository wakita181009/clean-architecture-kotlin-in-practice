package com.wakita181009.clean.domain.error

import com.wakita181009.clean.domain.model.Currency

sealed interface MoneyError : DomainError {
    data class CurrencyMismatch(
        val left: Currency,
        val right: Currency,
    ) : MoneyError {
        override val message: String = "Currency mismatch: $left vs $right"
    }

    data object InvalidJpyScale : MoneyError {
        override val message: String = "JPY cannot have decimal places"
    }
}
