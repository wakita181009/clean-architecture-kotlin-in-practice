package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.ValidationError

@JvmInline
value class IdempotencyKey private constructor(val value: String) {
    companion object {
        operator fun invoke(value: String): Either<ValidationError, IdempotencyKey> =
            if (value.isNotBlank()) IdempotencyKey(value).right()
            else ValidationError.BlankField("idempotencyKey").left()
    }
}
