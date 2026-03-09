package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Discount
import com.wakita181009.clean.domain.model.SubscriptionId
import java.time.Instant

interface DiscountCodePort {
    fun resolve(
        code: String,
        subscriptionId: SubscriptionId,
        appliedAt: Instant,
    ): Either<DomainError, Discount?>
}
