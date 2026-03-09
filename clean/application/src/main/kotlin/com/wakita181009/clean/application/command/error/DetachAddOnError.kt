package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface DetachAddOnError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : DetachAddOnError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : DetachAddOnError {
        override val message: String = "Subscription not found"
    }

    data object InvalidStatus : DetachAddOnError {
        override val message: String = "Subscription must be in Active or Paused status"
    }

    data object AddOnNotAttached : DetachAddOnError {
        override val message: String = "Add-on not attached or already detached"
    }

    data class Domain(val error: DomainError) : DetachAddOnError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : DetachAddOnError {
        override val message: String = "Internal error: $cause"
    }
}
