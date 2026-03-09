package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.ValidationError

@JvmInline
value class CustomerId private constructor(val value: Long) {
    companion object {
        operator fun invoke(value: Long): Either<ValidationError, CustomerId> =
            if (value > 0) CustomerId(value).right()
            else ValidationError.InvalidId("CustomerId", value).left()
    }
}
