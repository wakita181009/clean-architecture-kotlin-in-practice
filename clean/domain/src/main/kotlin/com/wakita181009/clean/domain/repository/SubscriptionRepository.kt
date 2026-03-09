package com.wakita181009.clean.domain.repository

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Subscription

interface SubscriptionRepository {
    fun save(subscription: Subscription): Either<DomainError, Subscription>
}
