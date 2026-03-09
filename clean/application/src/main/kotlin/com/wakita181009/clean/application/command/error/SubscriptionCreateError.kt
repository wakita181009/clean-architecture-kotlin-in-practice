package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface SubscriptionCreateError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : SubscriptionCreateError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object PlanNotFound : SubscriptionCreateError {
        override val message: String = "Plan not found"
    }

    data object AlreadySubscribed : SubscriptionCreateError {
        override val message: String = "Customer already has an active subscription"
    }

    data object InvalidDiscountCode : SubscriptionCreateError {
        override val message: String = "Invalid discount code"
    }

    data class Domain(val error: DomainError) : SubscriptionCreateError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : SubscriptionCreateError {
        override val message: String = "Internal error: $cause"
    }
}
