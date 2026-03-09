package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface PauseSubscriptionError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : PauseSubscriptionError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : PauseSubscriptionError {
        override val message: String = "Subscription not found"
    }

    data object NotActive : PauseSubscriptionError {
        override val message: String = "Subscription must be in Active status"
    }

    data object PauseLimitReached : PauseSubscriptionError {
        override val message: String = "Pause limit reached (maximum 2 pauses per billing period)"
    }

    data class Domain(val error: DomainError) : PauseSubscriptionError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : PauseSubscriptionError {
        override val message: String = "Internal error: $cause"
    }
}
