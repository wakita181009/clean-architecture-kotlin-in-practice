package com.wakita181009.clean.infrastructure.command.repository

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.clean.application.command.port.UsageQueryPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.IdempotencyKey
import com.wakita181009.clean.domain.model.MetricName
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.UsageId
import com.wakita181009.clean.domain.model.UsageRecord
import com.wakita181009.clean.domain.repository.UsageRecordRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.references.USAGE_RECORDS
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneOffset

@Repository
class UsageRecordRepositoryImpl(
    private val dsl: DSLContext,
) : UsageRecordRepository, UsageQueryPort {

    override fun save(usageRecord: UsageRecord): Either<DomainError, UsageRecord> = Either.catch {
        val record = dsl.insertInto(USAGE_RECORDS)
            .set(USAGE_RECORDS.SUBSCRIPTION_ID, usageRecord.subscriptionId.value)
            .set(USAGE_RECORDS.METRIC_NAME, usageRecord.metricName.value)
            .set(USAGE_RECORDS.QUANTITY, usageRecord.quantity)
            .set(USAGE_RECORDS.RECORDED_AT, usageRecord.recordedAt.atOffset(ZoneOffset.UTC))
            .set(USAGE_RECORDS.IDEMPOTENCY_KEY, usageRecord.idempotencyKey.value)
            .returningResult(USAGE_RECORDS.ID)
            .fetchOne()!!

        usageRecord.copy(id = UsageId(record.get(USAGE_RECORDS.ID)!!).getOrNull()!!)
    }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown DB error") }

    override fun sumQuantityForPeriod(
        subscriptionId: SubscriptionId,
        periodStart: Instant,
        periodEnd: Instant,
    ): Either<DomainError, Long> = Either.catch {
        val result = dsl.select(DSL.coalesce(DSL.sum(USAGE_RECORDS.QUANTITY), 0))
            .from(USAGE_RECORDS)
            .where(USAGE_RECORDS.SUBSCRIPTION_ID.eq(subscriptionId.value))
            .and(USAGE_RECORDS.RECORDED_AT.ge(periodStart.atOffset(ZoneOffset.UTC)))
            .and(USAGE_RECORDS.RECORDED_AT.lt(periodEnd.atOffset(ZoneOffset.UTC)))
            .fetchOne()!!
        (result.value1() as Number).toLong()
    }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown DB error") }

    override fun findByIdempotencyKey(key: IdempotencyKey): Either<DomainError, UsageRecord?> = either {
        val record = Either.catch {
            dsl.selectFrom(USAGE_RECORDS)
                .where(USAGE_RECORDS.IDEMPOTENCY_KEY.eq(key.value))
                .fetchOne()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        if (record == null) {
            null
        } else {
            val usageId = UsageId(record.id!!).mapLeft { it as DomainError }.bind()
            val subId = SubscriptionId(record.subscriptionId!!).mapLeft { it as DomainError }.bind()
            val metricName = MetricName(record.metricName!!).mapLeft { it as DomainError }.bind()
            val idempotencyKey = IdempotencyKey(record.idempotencyKey!!).mapLeft { it as DomainError }.bind()

            UsageRecord(
                id = usageId,
                subscriptionId = subId,
                metricName = metricName,
                quantity = record.quantity!!,
                recordedAt = record.recordedAt!!.toInstant(),
                idempotencyKey = idempotencyKey,
            )
        }
    }

    override fun findForPeriod(
        subscriptionId: SubscriptionId,
        periodStart: Instant,
        periodEnd: Instant,
    ): Either<DomainError, List<UsageRecord>> = either {
        val records = Either.catch {
            dsl.selectFrom(USAGE_RECORDS)
                .where(USAGE_RECORDS.SUBSCRIPTION_ID.eq(subscriptionId.value))
                .and(USAGE_RECORDS.RECORDED_AT.ge(periodStart.atOffset(ZoneOffset.UTC)))
                .and(USAGE_RECORDS.RECORDED_AT.lt(periodEnd.atOffset(ZoneOffset.UTC)))
                .fetch()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        records.map { record ->
            val usageId = UsageId(record.id!!).mapLeft { it as DomainError }.bind()
            val subId = SubscriptionId(record.subscriptionId!!).mapLeft { it as DomainError }.bind()
            val metricName = MetricName(record.metricName!!).mapLeft { it as DomainError }.bind()
            val idempotencyKey = IdempotencyKey(record.idempotencyKey!!).mapLeft { it as DomainError }.bind()

            UsageRecord(
                id = usageId,
                subscriptionId = subId,
                metricName = metricName,
                quantity = record.quantity!!,
                recordedAt = record.recordedAt!!.toInstant(),
                idempotencyKey = idempotencyKey,
            )
        }
    }
}
