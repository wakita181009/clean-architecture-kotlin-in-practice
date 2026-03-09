package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.ValidationError

@JvmInline
value class MetricName private constructor(val value: String) {
    companion object {
        operator fun invoke(value: String): Either<ValidationError, MetricName> =
            if (value.isNotBlank()) MetricName(value).right()
            else ValidationError.BlankField("metricName").left()
    }
}
