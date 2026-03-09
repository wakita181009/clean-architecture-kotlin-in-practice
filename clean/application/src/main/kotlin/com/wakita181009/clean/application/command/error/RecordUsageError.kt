package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface RecordUsageError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : RecordUsageError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : RecordUsageError {
        override val message: String = "Subscription not found"
    }

    data object NotActive : RecordUsageError {
        override val message: String = "Subscription must be in Active status"
    }

    data class UsageLimitExceeded(val currentUsage: Long, val limit: Int, val requested: Int) : RecordUsageError {
        override val message: String = "Usage limit exceeded: current=$currentUsage + requested=$requested > limit=$limit"
    }

    data class Domain(val error: DomainError) : RecordUsageError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : RecordUsageError {
        override val message: String = "Internal error: $cause"
    }
}
