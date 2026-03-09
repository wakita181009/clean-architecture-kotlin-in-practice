package com.wakita181009.clean.domain.error

sealed interface ValidationError : DomainError {
    data class InvalidId(val field: String, val value: Long) : ValidationError {
        override val message: String = "$field must be positive, got $value"
    }

    data class BlankField(val field: String) : ValidationError {
        override val message: String = "$field must not be blank"
    }

    data class InvalidQuantity(val value: Int) : ValidationError {
        override val message: String = "Quantity must be at least 1, got $value"
    }
}
