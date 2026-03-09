package com.wakita181009.clean.application.query.repository

import arrow.core.Either
import com.wakita181009.clean.application.query.dto.SubscriptionDto
import com.wakita181009.clean.application.query.error.SubscriptionFindByIdQueryError
import com.wakita181009.clean.application.query.error.SubscriptionListByCustomerQueryError

interface SubscriptionQueryRepository {
    fun findById(id: Long): Either<SubscriptionFindByIdQueryError, SubscriptionDto>
    fun listByCustomerId(customerId: Long): Either<SubscriptionListByCustomerQueryError, List<SubscriptionDto>>
}
