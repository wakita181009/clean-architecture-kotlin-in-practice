package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.ValidationError

@JvmInline
value class PlanId private constructor(val value: Long) {
    companion object {
        operator fun invoke(value: Long): Either<ValidationError, PlanId> =
            if (value > 0) PlanId(value).right()
            else ValidationError.InvalidId("PlanId", value).left()
    }
}
