package com.wakita181009.classic.repository

import com.wakita181009.classic.model.UsageRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface UsageRecordRepository : JpaRepository<UsageRecord, Long> {
    fun findByIdempotencyKey(key: String): UsageRecord?

    fun findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
        subscriptionId: Long,
        start: Instant,
        end: Instant,
    ): List<UsageRecord>
}
