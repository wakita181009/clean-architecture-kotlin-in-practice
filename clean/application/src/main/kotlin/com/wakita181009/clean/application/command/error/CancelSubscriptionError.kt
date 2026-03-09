package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface CancelSubscriptionError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : CancelSubscriptionError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : CancelSubscriptionError {
        override val message: String = "Subscription not found"
    }

    data object AlreadyTerminal : CancelSubscriptionError {
        override val message: String = "Subscription is already canceled or expired"
    }

    data object CannotEndOfPeriodForPaused : CancelSubscriptionError {
        override val message: String = "Paused subscriptions can only be canceled immediately"
    }

    data class Domain(val error: DomainError) : CancelSubscriptionError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : CancelSubscriptionError {
        override val message: String = "Internal error: $cause"
    }
}
