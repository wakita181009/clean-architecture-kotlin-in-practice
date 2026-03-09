package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId

interface PlanQueryPort {
    fun findActiveById(id: PlanId): Either<DomainError, Plan?>
}
