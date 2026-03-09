package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.ValidationError

@JvmInline
value class DiscountId private constructor(val value: Long) {
    companion object {
        operator fun invoke(value: Long): Either<ValidationError, DiscountId> =
            if (value > 0) DiscountId(value).right()
            else ValidationError.InvalidId("DiscountId", value).left()
    }
}
