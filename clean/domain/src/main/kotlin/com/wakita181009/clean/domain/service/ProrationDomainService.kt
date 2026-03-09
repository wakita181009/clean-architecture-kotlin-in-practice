package com.wakita181009.clean.domain.service

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.InvoiceLineItem
import com.wakita181009.clean.domain.model.InvoiceLineItemType
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.ProrationResult

class ProrationDomainService {

    fun calculateProration(
        currentPlan: Plan,
        newPlan: Plan,
        daysRemaining: Long,
        totalDaysInPeriod: Long,
    ): Either<DomainError, ProrationResult> = either {
        val credit = currentPlan.basePrice.multiply(daysRemaining, totalDaysInPeriod)
        val charge = newPlan.basePrice.multiply(daysRemaining, totalDaysInPeriod)

        val creditLineItem = InvoiceLineItem(
            description = "Proration credit for ${currentPlan.name}",
            amount = credit.negate(),
            type = InvoiceLineItemType.PRORATION_CREDIT,
        )

        val chargeLineItem = InvoiceLineItem(
            description = "Proration charge for ${newPlan.name}",
            amount = charge,
            type = InvoiceLineItemType.PRORATION_CHARGE,
        )

        val netAmount = (charge - credit).bind()

        ProrationResult(
            credit = creditLineItem,
            charge = chargeLineItem,
            netAmount = netAmount,
        )
    }
}
