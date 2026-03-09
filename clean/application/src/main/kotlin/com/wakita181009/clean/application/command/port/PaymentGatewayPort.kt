package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.application.command.dto.PaymentError
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.PaymentMethod

interface PaymentGatewayPort {
    fun charge(
        amount: Money,
        paymentMethod: PaymentMethod,
        customerRef: String,
    ): Either<PaymentError, PaymentResult>
}
