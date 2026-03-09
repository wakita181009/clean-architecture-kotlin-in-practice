package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface ResumeSubscriptionError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : ResumeSubscriptionError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : ResumeSubscriptionError {
        override val message: String = "Subscription not found"
    }

    data object NotPaused : ResumeSubscriptionError {
        override val message: String = "Subscription must be in Paused status"
    }

    data class Domain(val error: DomainError) : ResumeSubscriptionError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : ResumeSubscriptionError {
        override val message: String = "Internal error: $cause"
    }
}
