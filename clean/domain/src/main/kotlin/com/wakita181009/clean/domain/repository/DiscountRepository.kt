package com.wakita181009.clean.domain.repository

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Discount

interface DiscountRepository {
    fun save(discount: Discount): Either<DomainError, Discount>
}
