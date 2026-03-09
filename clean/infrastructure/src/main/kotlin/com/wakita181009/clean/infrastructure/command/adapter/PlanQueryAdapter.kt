package com.wakita181009.clean.infrastructure.command.adapter

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.clean.application.command.port.PlanQueryPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import org.jooq.DSLContext
import org.jooq.generated.tables.references.PLANS
import org.springframework.stereotype.Component

@Component
class PlanQueryAdapter(
    private val dsl: DSLContext,
) : PlanQueryPort {

    override fun findActiveById(id: PlanId): Either<DomainError, Plan?> = either {
        val record = dsl.selectFrom(PLANS)
            .where(PLANS.ID.eq(id.value))
            .and(PLANS.ACTIVE.eq(true))
            .fetchOne()
            ?: return@either null

        val currency = Currency.valueOf(record.basePriceCurrency!!)
        val money = Money.of(record.basePriceAmount!!, currency).bind()
        val features = record.features!!.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

        Plan.of(
            id = id,
            name = record.name!!,
            billingInterval = BillingInterval.valueOf(record.billingInterval!!),
            basePrice = money,
            usageLimit = record.usageLimit,
            features = features,
            tier = PlanTier.valueOf(record.tier!!),
            active = record.active!!,
        ).mapLeft { it as DomainError }.bind()
    }
}
