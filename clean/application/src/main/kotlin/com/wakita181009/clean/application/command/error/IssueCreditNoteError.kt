package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface IssueCreditNoteError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : IssueCreditNoteError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object InvoiceNotFound : IssueCreditNoteError {
        override val message: String = "Invoice not found"
    }

    data object InvoiceNotPaid : IssueCreditNoteError {
        override val message: String = "Invoice must be in Paid status"
    }

    data object AlreadyFullyRefunded : IssueCreditNoteError {
        override val message: String = "Invoice has already been fully refunded"
    }

    data class AmountExceedsRemaining(val remaining: String) : IssueCreditNoteError {
        override val message: String = "Amount exceeds remaining refundable amount ($remaining)"
    }

    data class PaymentFailed(val reason: String) : IssueCreditNoteError {
        override val message: String = "Refund failed: $reason"
    }

    data class Domain(val error: DomainError) : IssueCreditNoteError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : IssueCreditNoteError {
        override val message: String = "Internal error: $cause"
    }
}
