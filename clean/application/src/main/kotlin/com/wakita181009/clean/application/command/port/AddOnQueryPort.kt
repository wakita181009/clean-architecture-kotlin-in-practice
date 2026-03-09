package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.AddOn
import com.wakita181009.clean.domain.model.AddOnId

interface AddOnQueryPort {
    fun findActiveById(id: AddOnId): Either<DomainError, AddOn?>
}
