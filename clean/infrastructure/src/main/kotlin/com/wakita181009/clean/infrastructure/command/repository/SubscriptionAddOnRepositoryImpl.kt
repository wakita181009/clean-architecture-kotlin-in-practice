package com.wakita181009.clean.infrastructure.command.repository

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.port.SubscriptionAddOnCommandQueryPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.AddOnId
import com.wakita181009.clean.domain.model.SubscriptionAddOn
import com.wakita181009.clean.domain.model.SubscriptionAddOnId
import com.wakita181009.clean.domain.model.SubscriptionAddOnStatus
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.repository.SubscriptionAddOnRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.records.SubscriptionAddonsRecord
import org.jooq.generated.tables.references.SUBSCRIPTION_ADDONS
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class SubscriptionAddOnRepositoryImpl(
    private val dsl: DSLContext,
) : SubscriptionAddOnRepository, SubscriptionAddOnCommandQueryPort {

    override fun save(subscriptionAddOn: SubscriptionAddOn): Either<DomainError, SubscriptionAddOn> = Either.catch {
        if (subscriptionAddOn.id == null) {
            val record = dsl.insertInto(SUBSCRIPTION_ADDONS)
                .set(SUBSCRIPTION_ADDONS.SUBSCRIPTION_ID, subscriptionAddOn.subscriptionId.value)
                .set(SUBSCRIPTION_ADDONS.ADDON_ID, subscriptionAddOn.addOnId.value)
                .set(SUBSCRIPTION_ADDONS.QUANTITY, subscriptionAddOn.quantity)
                .set(SUBSCRIPTION_ADDONS.STATUS, toDbStatus(subscriptionAddOn.status))
                .set(SUBSCRIPTION_ADDONS.ATTACHED_AT, subscriptionAddOn.attachedAt.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTION_ADDONS.DETACHED_AT, subscriptionAddOn.detachedAt?.atOffset(ZoneOffset.UTC))
                .returningResult(SUBSCRIPTION_ADDONS.ID)
                .fetchOne()!!

            subscriptionAddOn.copy(id = SubscriptionAddOnId(record.get(SUBSCRIPTION_ADDONS.ID)!!).getOrNull()!!)
        } else {
            dsl.update(SUBSCRIPTION_ADDONS)
                .set(SUBSCRIPTION_ADDONS.QUANTITY, subscriptionAddOn.quantity)
                .set(SUBSCRIPTION_ADDONS.STATUS, toDbStatus(subscriptionAddOn.status))
                .set(SUBSCRIPTION_ADDONS.DETACHED_AT, subscriptionAddOn.detachedAt?.atOffset(ZoneOffset.UTC))
                .where(SUBSCRIPTION_ADDONS.ID.eq(subscriptionAddOn.id!!.value))
                .execute()
            subscriptionAddOn
        }
    }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown DB error") }

    override fun findActiveBySubscriptionId(subscriptionId: SubscriptionId): Either<DomainError, List<SubscriptionAddOn>> = either {
        val records = Either.catch {
            dsl.selectFrom(SUBSCRIPTION_ADDONS)
                .where(SUBSCRIPTION_ADDONS.SUBSCRIPTION_ID.eq(subscriptionId.value))
                .and(SUBSCRIPTION_ADDONS.STATUS.eq("ACTIVE"))
                .fetch()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        records.map { mapToSubscriptionAddOn(it).bind() }
    }

    override fun findActiveBySubscriptionIdAndAddOnId(
        subscriptionId: SubscriptionId,
        addOnId: AddOnId,
    ): Either<DomainError, SubscriptionAddOn?> = either {
        val record = Either.catch {
            dsl.selectFrom(SUBSCRIPTION_ADDONS)
                .where(SUBSCRIPTION_ADDONS.SUBSCRIPTION_ID.eq(subscriptionId.value))
                .and(SUBSCRIPTION_ADDONS.ADDON_ID.eq(addOnId.value))
                .and(SUBSCRIPTION_ADDONS.STATUS.eq("ACTIVE"))
                .fetchOne()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        if (record != null) mapToSubscriptionAddOn(record).bind() else null
    }

    private fun mapToSubscriptionAddOn(record: SubscriptionAddonsRecord): Either<DomainError, SubscriptionAddOn> = either {
        val id = SubscriptionAddOnId(record.id!!).mapLeft { it as DomainError }.bind()
        val subscriptionId = SubscriptionId(record.subscriptionId!!).mapLeft { it as DomainError }.bind()
        val addOnId = AddOnId(record.addonId!!).mapLeft { it as DomainError }.bind()

        val status = toDomainStatus(record.status!!, record.detachedAt?.toInstant()).bind()

        SubscriptionAddOn(
            id = id,
            subscriptionId = subscriptionId,
            addOnId = addOnId,
            quantity = record.quantity!!,
            status = status,
            attachedAt = record.attachedAt!!.toInstant(),
            detachedAt = record.detachedAt?.toInstant(),
        )
    }

    companion object {
        fun toDbStatus(status: SubscriptionAddOnStatus): String = when (status) {
            is SubscriptionAddOnStatus.Active -> "ACTIVE"
            is SubscriptionAddOnStatus.Detached -> "DETACHED"
        }

        fun toDomainStatus(
            dbStatus: String,
            detachedAt: java.time.Instant?,
        ): Either<DomainError, SubscriptionAddOnStatus> = when (dbStatus) {
            "ACTIVE" -> Either.Right(SubscriptionAddOnStatus.Active)
            "DETACHED" -> Either.Right(SubscriptionAddOnStatus.Detached(detachedAt ?: java.time.Instant.EPOCH))
            else -> Either.Left(InfraError.UnknownValue("SubscriptionAddOnStatus", dbStatus))
        }
    }
}
