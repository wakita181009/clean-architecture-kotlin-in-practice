package com.wakita181009.clean.presentation.controller

import com.wakita181009.clean.application.command.error.RecoverPaymentError
import com.wakita181009.clean.application.command.usecase.RecoverPaymentUseCase
import com.wakita181009.clean.application.query.error.InvoiceListBySubscriptionQueryError
import com.wakita181009.clean.application.query.usecase.InvoiceListBySubscriptionQueryUseCase
import com.wakita181009.clean.presentation.dto.ErrorResponse
import com.wakita181009.clean.presentation.dto.InvoiceResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invoices")
class InvoiceController(
    private val recoverPaymentUseCase: RecoverPaymentUseCase,
    private val invoiceListBySubscriptionQueryUseCase: InvoiceListBySubscriptionQueryUseCase,
) {

    @PostMapping("/{id}/pay")
    fun recoverPayment(@PathVariable id: Long): ResponseEntity<*> =
        recoverPaymentUseCase.execute(id).fold(
            ifLeft = { error ->
                when (error) {
                    is RecoverPaymentError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is RecoverPaymentError.InvoiceNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is RecoverPaymentError.InvoiceNotOpen -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is RecoverPaymentError.SubscriptionNotPastDue -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is RecoverPaymentError.GracePeriodExpired -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is RecoverPaymentError.PaymentFailed -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse(error.message))
                    is RecoverPaymentError.Domain -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                    is RecoverPaymentError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { invoice ->
                ResponseEntity.ok(InvoiceResponse.from(invoice))
            },
        )

    @GetMapping
    fun listBySubscription(@RequestParam subscriptionId: Long): ResponseEntity<*> =
        invoiceListBySubscriptionQueryUseCase.execute(subscriptionId).fold(
            ifLeft = { error ->
                when (error) {
                    is InvoiceListBySubscriptionQueryError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is InvoiceListBySubscriptionQueryError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { dtos ->
                ResponseEntity.ok(dtos.map { InvoiceResponse.from(it) })
            },
        )
}
