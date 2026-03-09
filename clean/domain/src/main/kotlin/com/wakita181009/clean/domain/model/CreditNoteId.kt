package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.ValidationError

@JvmInline
value class CreditNoteId private constructor(val value: Long) {
    companion object {
        operator fun invoke(value: Long): Either<ValidationError, CreditNoteId> =
            if (value > 0) CreditNoteId(value).right()
            else ValidationError.InvalidId("CreditNoteId", value).left()
    }
}
