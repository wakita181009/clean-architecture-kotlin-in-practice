package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Discount
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId

interface SubscriptionCommandQueryPort {
    fun findById(id: SubscriptionId): Either<DomainError, Subscription>
    fun findActiveByCustomerId(customerId: CustomerId): Either<DomainError, Subscription?>
    fun findByIdWithDiscount(id: SubscriptionId): Either<DomainError, Pair<Subscription, Discount?>>
}
