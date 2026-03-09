package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.right
import com.wakita181009.clean.domain.error.SubscriptionAddOnError
import java.time.Instant

sealed interface SubscriptionAddOnStatus {

    val name: String

    data object Active : SubscriptionAddOnStatus {
        override val name: String = "ACTIVE"

        fun detach(detachedAt: Instant): Either<SubscriptionAddOnError, Detached> =
            Detached(detachedAt).right()
    }

    data class Detached(val detachedAt: Instant) : SubscriptionAddOnStatus {
        override val name: String = "DETACHED"
    }
}
