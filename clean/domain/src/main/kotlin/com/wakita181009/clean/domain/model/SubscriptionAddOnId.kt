package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.ValidationError

@JvmInline
value class SubscriptionAddOnId private constructor(val value: Long) {
    companion object {
        operator fun invoke(value: Long): Either<ValidationError, SubscriptionAddOnId> =
            if (value > 0) SubscriptionAddOnId(value).right()
            else ValidationError.InvalidId("SubscriptionAddOnId", value).left()
    }
}
