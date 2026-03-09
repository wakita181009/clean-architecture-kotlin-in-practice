package com.wakita181009.clean.presentation.controller

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.error.RecoverPaymentError
import com.wakita181009.clean.application.command.usecase.RecoverPaymentUseCase
import com.wakita181009.clean.application.query.dto.InvoiceDto
import com.wakita181009.clean.application.query.dto.InvoiceLineItemDto
import com.wakita181009.clean.application.query.error.InvoiceListBySubscriptionQueryError
import com.wakita181009.clean.application.query.usecase.InvoiceListBySubscriptionQueryUseCase
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.SubscriptionId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class InvoiceControllerTest : DescribeSpec({

    val recoverPaymentUseCase = mockk<RecoverPaymentUseCase>()
    val issueCreditNoteUseCase = mockk<com.wakita181009.clean.application.command.usecase.IssueCreditNoteUseCase>()
    val invoiceListBySubscriptionQueryUseCase = mockk<InvoiceListBySubscriptionQueryUseCase>()
    val creditNoteListQueryUseCase = mockk<com.wakita181009.clean.application.query.usecase.CreditNoteListQueryUseCase>()

    val controller = InvoiceController(
        recoverPaymentUseCase = recoverPaymentUseCase,
        issueCreditNoteUseCase = issueCreditNoteUseCase,
        invoiceListBySubscriptionQueryUseCase = invoiceListBySubscriptionQueryUseCase,
        creditNoteListQueryUseCase = creditNoteListQueryUseCase,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")

    fun paidInvoice() = Invoice(
        id = InvoiceId(1L).getOrNull()!!,
        subscriptionId = SubscriptionId(1L).getOrNull()!!,
        lineItems = emptyList(),
        subtotal = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        discountAmount = Money.zero(Currency.USD),
        total = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        currency = Currency.USD,
        status = InvoiceStatus.Paid(now),
        dueDate = LocalDate.of(2025, 1, 15),
        paidAt = now,
        paymentAttemptCount = 2,
        createdAt = now,
        updatedAt = now,
    )

    fun invoiceDto() = InvoiceDto(
        id = 1L,
        subscriptionId = 1L,
        lineItems = listOf(
            InvoiceLineItemDto(description = "Plan charge", amount = "49.99", currency = "USD", type = "PLAN_CHARGE"),
        ),
        subtotalAmount = "49.99",
        subtotalCurrency = "USD",
        discountAmount = "0.00",
        totalAmount = "49.99",
        totalCurrency = "USD",
        status = "PAID",
        dueDate = LocalDate.of(2025, 1, 15),
        paidAt = now,
        paymentAttemptCount = 1,
        createdAt = now,
    )

    beforeTest {
        clearMocks(recoverPaymentUseCase, invoiceListBySubscriptionQueryUseCase)
    }

    describe("POST /api/invoices/{id}/pay") {
        // API-RP1
        it("returns 200 on successful payment recovery") {
            every { recoverPaymentUseCase.execute(1L) } returns paidInvoice().right()
            val response = controller.recoverPayment(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-RP2
        it("returns 409 when grace period expired") {
            every { recoverPaymentUseCase.execute(1L) } returns RecoverPaymentError.GracePeriodExpired.left()
            val response = controller.recoverPayment(1L)
            response.statusCode shouldBe HttpStatus.CONFLICT
        }

        // API-RP3
        it("returns 502 on gateway failure") {
            every { recoverPaymentUseCase.execute(1L) } returns RecoverPaymentError.PaymentFailed("declined").left()
            val response = controller.recoverPayment(1L)
            response.statusCode shouldBe HttpStatus.BAD_GATEWAY
        }

        it("returns 404 when invoice not found") {
            every { recoverPaymentUseCase.execute(999L) } returns RecoverPaymentError.InvoiceNotFound.left()
            val response = controller.recoverPayment(999L)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("returns 400 for invalid input") {
            every { recoverPaymentUseCase.execute(0L) } returns
                RecoverPaymentError.InvalidInput("invoiceId", "must be positive").left()
            val response = controller.recoverPayment(0L)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }

    describe("GET /api/invoices?subscriptionId=") {
        // API-G4
        it("returns 200 with invoice list") {
            every { invoiceListBySubscriptionQueryUseCase.execute(1L) } returns listOf(invoiceDto()).right()
            val response = controller.listBySubscription(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        it("returns 400 for invalid subscriptionId") {
            every { invoiceListBySubscriptionQueryUseCase.execute(0L) } returns
                InvoiceListBySubscriptionQueryError.InvalidInput("subscriptionId", "must be positive").left()
            val response = controller.listBySubscription(0L)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }
})
