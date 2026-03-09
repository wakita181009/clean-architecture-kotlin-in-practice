package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.SubscriptionError
import java.time.Instant

sealed interface SubscriptionStatus {

    val name: String

    data object Trial : SubscriptionStatus {
        override val name: String = "TRIAL"

        fun activate(): Either<SubscriptionError, Active> =
            Active.right()

        fun cancel(canceledAt: Instant): Either<SubscriptionError, Canceled> =
            Canceled(canceledAt).right()

        fun expire(): Either<SubscriptionError, Expired> =
            Expired.right()
    }

    data object Active : SubscriptionStatus {
        override val name: String = "ACTIVE"

        fun pause(pausedAt: Instant, pauseCountInPeriod: Int): Either<SubscriptionError, Paused> =
            if (pauseCountInPeriod >= 2) {
                SubscriptionError.PauseLimitReached.left()
            } else {
                Paused(pausedAt).right()
            }

        fun markPastDue(gracePeriodEnd: Instant): Either<SubscriptionError, PastDue> =
            PastDue(gracePeriodEnd).right()

        fun cancel(canceledAt: Instant): Either<SubscriptionError, Canceled> =
            Canceled(canceledAt).right()
    }

    data class Paused(val pausedAt: Instant) : SubscriptionStatus {
        override val name: String = "PAUSED"

        fun resume(): Either<SubscriptionError, Active> =
            Active.right()

        fun cancel(canceledAt: Instant): Either<SubscriptionError, Canceled> =
            Canceled(canceledAt).right()
    }

    data class PastDue(val gracePeriodEnd: Instant) : SubscriptionStatus {
        override val name: String = "PAST_DUE"

        fun recover(): Either<SubscriptionError, Active> =
            Active.right()

        fun cancel(canceledAt: Instant): Either<SubscriptionError, Canceled> =
            Canceled(canceledAt).right()
    }

    data class Canceled(val canceledAt: Instant) : SubscriptionStatus {
        override val name: String = "CANCELED"
    }

    data object Expired : SubscriptionStatus {
        override val name: String = "EXPIRED"
    }
}
