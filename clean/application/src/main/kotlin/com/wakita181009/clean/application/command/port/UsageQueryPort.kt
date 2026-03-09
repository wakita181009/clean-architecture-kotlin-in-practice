package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.IdempotencyKey
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.UsageRecord
import java.time.Instant

interface UsageQueryPort {
    fun sumQuantityForPeriod(
        subscriptionId: SubscriptionId,
        periodStart: Instant,
        periodEnd: Instant,
    ): Either<DomainError, Long>

    fun findByIdempotencyKey(key: IdempotencyKey): Either<DomainError, UsageRecord?>

    fun findForPeriod(
        subscriptionId: SubscriptionId,
        periodStart: Instant,
        periodEnd: Instant,
    ): Either<DomainError, List<UsageRecord>>
}
