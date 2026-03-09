package com.wakita181009.clean.domain.repository

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.UsageRecord

interface UsageRecordRepository {
    fun save(usageRecord: UsageRecord): Either<DomainError, UsageRecord>
}
