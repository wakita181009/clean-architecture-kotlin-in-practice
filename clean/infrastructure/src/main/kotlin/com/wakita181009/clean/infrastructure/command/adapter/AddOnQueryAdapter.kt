package com.wakita181009.clean.infrastructure.command.adapter

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.clean.application.command.port.AddOnQueryPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.AddOn
import com.wakita181009.clean.domain.model.AddOnBillingType
import com.wakita181009.clean.domain.model.AddOnId
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.infrastructure.command.repository.InfraError
import org.jooq.DSLContext
import org.jooq.generated.tables.references.ADDONS
import org.springframework.stereotype.Component

@Component
class AddOnQueryAdapter(
    private val dsl: DSLContext,
) : AddOnQueryPort {

    override fun findActiveById(id: AddOnId): Either<DomainError, AddOn?> = either {
        val record = Either.catch {
            dsl.selectFrom(ADDONS)
                .where(ADDONS.ID.eq(id.value))
                .fetchOne()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        if (record == null) {
            null
        } else {
            val currency = Currency.valueOf(record.priceCurrency!!)
            val price = Money.of(record.priceAmount!!, currency).bind()

            val billingType = Either.catch { AddOnBillingType.valueOf(record.billingType!!) }
                .mapLeft { InfraError.UnknownValue("AddOnBillingType", record.billingType!!) as DomainError }
                .bind()

            val compatibleTiers = record.compatibleTiers!!.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map {
                    Either.catch { PlanTier.valueOf(it) }
                        .mapLeft { e -> InfraError.UnknownValue("PlanTier", it) as DomainError }
                        .bind()
                }
                .toSet()

            AddOn.of(
                id = id,
                name = record.name!!,
                price = price,
                billingType = billingType,
                compatibleTiers = compatibleTiers,
                active = record.active!!,
            ).mapLeft { it as DomainError }.bind()
        }
    }
}
