package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface PlanChangeError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : PlanChangeError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : PlanChangeError {
        override val message: String = "Subscription not found"
    }

    data object NotActive : PlanChangeError {
        override val message: String = "Subscription must be in Active status"
    }

    data object SamePlan : PlanChangeError {
        override val message: String = "New plan is the same as current plan"
    }

    data object PlanNotFound : PlanChangeError {
        override val message: String = "New plan not found or inactive"
    }

    data object CurrencyMismatch : PlanChangeError {
        override val message: String = "Cannot change currency during plan change"
    }

    data class PaymentFailed(val reason: String) : PlanChangeError {
        override val message: String = "Payment failed: $reason"
    }

    data class Domain(val error: DomainError) : PlanChangeError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : PlanChangeError {
        override val message: String = "Internal error: $cause"
    }
}
