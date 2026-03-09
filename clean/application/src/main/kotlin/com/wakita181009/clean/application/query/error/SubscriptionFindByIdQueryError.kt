package com.wakita181009.clean.application.query.error

import com.wakita181009.clean.application.error.ApplicationError

sealed interface SubscriptionFindByIdQueryError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : SubscriptionFindByIdQueryError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object NotFound : SubscriptionFindByIdQueryError {
        override val message: String = "Subscription not found"
    }

    data class Internal(val cause: String) : SubscriptionFindByIdQueryError {
        override val message: String = "Internal error: $cause"
    }
}
