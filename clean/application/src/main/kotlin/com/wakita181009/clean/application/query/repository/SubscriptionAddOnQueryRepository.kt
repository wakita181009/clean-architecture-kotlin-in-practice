package com.wakita181009.clean.application.query.repository

import arrow.core.Either
import com.wakita181009.clean.application.query.dto.SubscriptionAddOnDto
import com.wakita181009.clean.application.query.error.SubscriptionAddOnListQueryError

interface SubscriptionAddOnQueryRepository {
    fun findBySubscriptionId(subscriptionId: Long): Either<SubscriptionAddOnListQueryError, List<SubscriptionAddOnDto>>
}
