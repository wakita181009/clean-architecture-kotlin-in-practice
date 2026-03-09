package com.wakita181009.clean.infrastructure.command.adapter

import arrow.core.Either
import arrow.core.right
import com.wakita181009.clean.application.command.dto.PaymentError
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.application.command.dto.RefundResult
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.PaymentMethod
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class PaymentGatewayAdapter : PaymentGatewayPort {

    override fun charge(
        amount: Money,
        paymentMethod: PaymentMethod,
        customerRef: String,
    ): Either<PaymentError, PaymentResult> =
        PaymentResult(
            transactionId = UUID.randomUUID().toString(),
            processedAt = Instant.now(),
        ).right()

    override fun refund(
        originalTransactionId: String,
        amount: Money,
    ): Either<PaymentError, RefundResult> =
        RefundResult(
            refundTransactionId = "refund_${UUID.randomUUID()}",
            processedAt = Instant.now(),
        ).right()
}
