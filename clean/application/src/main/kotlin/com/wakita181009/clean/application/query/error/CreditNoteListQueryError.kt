package com.wakita181009.clean.application.query.error

import com.wakita181009.clean.application.error.ApplicationError

sealed interface CreditNoteListQueryError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : CreditNoteListQueryError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data class Internal(val cause: String) : CreditNoteListQueryError {
        override val message: String = "Internal error: $cause"
    }
}
