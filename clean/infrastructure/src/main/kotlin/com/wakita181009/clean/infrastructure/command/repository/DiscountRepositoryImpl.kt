package com.wakita181009.clean.infrastructure.command.repository

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.clean.application.command.port.DiscountCodePort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Discount
import com.wakita181009.clean.domain.model.DiscountId
import com.wakita181009.clean.domain.model.DiscountType
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.repository.DiscountRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.references.DISCOUNTS
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneOffset

@Repository
class DiscountRepositoryImpl(
    private val dsl: DSLContext,
) : DiscountRepository, DiscountCodePort {

    override fun save(discount: Discount): Either<DomainError, Discount> = Either.catch {
        if (discount.id == null) {
            val record = dsl.insertInto(DISCOUNTS)
                .set(DISCOUNTS.SUBSCRIPTION_ID, discount.subscriptionId.value)
                .set(DISCOUNTS.TYPE, discount.type.name)
                .set(DISCOUNTS.PERCENTAGE_VALUE, discount.percentageValue?.toBigDecimal())
                .set(DISCOUNTS.FIXED_AMOUNT, discount.fixedAmountMoney?.amount)
                .set(DISCOUNTS.FIXED_CURRENCY, discount.fixedAmountMoney?.currency?.name)
                .set(DISCOUNTS.DURATION_MONTHS, discount.durationMonths)
                .set(DISCOUNTS.REMAINING_CYCLES, discount.remainingCycles)
                .set(DISCOUNTS.APPLIED_AT, discount.appliedAt.atOffset(ZoneOffset.UTC))
                .returningResult(DISCOUNTS.ID)
                .fetchOne()!!

            discount.copy(id = DiscountId(record.get(DISCOUNTS.ID)!!).getOrNull()!!)
        } else {
            dsl.update(DISCOUNTS)
                .set(DISCOUNTS.REMAINING_CYCLES, discount.remainingCycles)
                .where(DISCOUNTS.ID.eq(discount.id!!.value))
                .execute()
            discount
        }
    }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown DB error") }

    override fun resolve(
        code: String,
        subscriptionId: SubscriptionId,
        appliedAt: Instant,
    ): Either<DomainError, Discount?> = either {
        // Stub: in a real system, this would look up discount codes from a table
        // For now, we support a simple "WELCOME20" code as 20% for 3 months
        when (code) {
            "WELCOME20" -> Discount.of(
                id = null,
                subscriptionId = subscriptionId,
                type = DiscountType.PERCENTAGE,
                percentageValue = 20,
                fixedAmountMoney = null,
                durationMonths = 3,
                remainingCycles = 3,
                appliedAt = appliedAt,
            ).mapLeft { it as DomainError }.bind()
            else -> null
        }
    }
}
