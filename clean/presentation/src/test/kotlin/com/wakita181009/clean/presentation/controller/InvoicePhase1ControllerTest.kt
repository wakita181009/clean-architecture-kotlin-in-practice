package com.wakita181009.clean.presentation.controller

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.error.IssueCreditNoteError
import com.wakita181009.clean.application.command.usecase.IssueCreditNoteUseCase
import com.wakita181009.clean.application.command.usecase.RecoverPaymentUseCase
import com.wakita181009.clean.application.query.dto.CreditNoteDto
import com.wakita181009.clean.application.query.error.CreditNoteListQueryError
import com.wakita181009.clean.application.query.usecase.CreditNoteListQueryUseCase
import com.wakita181009.clean.application.query.usecase.InvoiceListBySubscriptionQueryUseCase
import com.wakita181009.clean.domain.model.CreditNote
import com.wakita181009.clean.domain.model.CreditNoteApplication
import com.wakita181009.clean.domain.model.CreditNoteId
import com.wakita181009.clean.domain.model.CreditNoteStatus
import com.wakita181009.clean.domain.model.CreditNoteType
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.presentation.dto.IssueCreditNoteRequest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Instant

class InvoicePhase1ControllerTest : DescribeSpec({

    val recoverPaymentUseCase = mockk<RecoverPaymentUseCase>()
    val issueCreditNoteUseCase = mockk<IssueCreditNoteUseCase>()
    val invoiceListBySubscriptionQueryUseCase = mockk<InvoiceListBySubscriptionQueryUseCase>()
    val creditNoteListQueryUseCase = mockk<CreditNoteListQueryUseCase>()

    val controller = InvoiceController(
        recoverPaymentUseCase = recoverPaymentUseCase,
        issueCreditNoteUseCase = issueCreditNoteUseCase,
        invoiceListBySubscriptionQueryUseCase = invoiceListBySubscriptionQueryUseCase,
        creditNoteListQueryUseCase = creditNoteListQueryUseCase,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")

    fun creditNote() = CreditNote(
        id = CreditNoteId(1L).getOrNull()!!, invoiceId = InvoiceId(1L).getOrNull()!!,
        subscriptionId = SubscriptionId(1L).getOrNull()!!,
        amount = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        reason = "Customer request", type = CreditNoteType.FULL,
        application = CreditNoteApplication.REFUND_TO_PAYMENT,
        status = CreditNoteStatus.Applied, refundTransactionId = "ref_1",
        createdAt = now, updatedAt = now,
    )

    beforeTest {
        clearMocks(issueCreditNoteUseCase, creditNoteListQueryUseCase)
    }

    describe("Issue Credit Note API") {
        // API-CN1
        it("returns 201 for full refund to payment") {
            every { issueCreditNoteUseCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason") } returns creditNote().right()
            val response = controller.issueCreditNote(1L, IssueCreditNoteRequest(type = "FULL", application = "REFUND_TO_PAYMENT", reason = "Reason"))
            response.statusCode shouldBe HttpStatus.CREATED
        }

        // API-CN2
        it("returns 201 for partial as account credit") {
            every { issueCreditNoteUseCase.execute(1L, "PARTIAL", "ACCOUNT_CREDIT", BigDecimal("20.00"), "Reason") } returns
                creditNote().copy(type = CreditNoteType.PARTIAL, application = CreditNoteApplication.ACCOUNT_CREDIT, amount = Money.of(BigDecimal("20.00"), Currency.USD).getOrNull()!!).right()
            val response = controller.issueCreditNote(1L, IssueCreditNoteRequest(type = "PARTIAL", application = "ACCOUNT_CREDIT", amount = BigDecimal("20.00"), reason = "Reason"))
            response.statusCode shouldBe HttpStatus.CREATED
        }

        // API-CN3
        it("returns 409 when invoice not paid") {
            every { issueCreditNoteUseCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason") } returns IssueCreditNoteError.InvoiceNotPaid.left()
            val response = controller.issueCreditNote(1L, IssueCreditNoteRequest(type = "FULL", application = "REFUND_TO_PAYMENT", reason = "Reason"))
            response.statusCode shouldBe HttpStatus.CONFLICT
        }

        // API-CN4
        it("returns 502 when gateway fails") {
            every { issueCreditNoteUseCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason") } returns IssueCreditNoteError.PaymentFailed("Declined").left()
            val response = controller.issueCreditNote(1L, IssueCreditNoteRequest(type = "FULL", application = "REFUND_TO_PAYMENT", reason = "Reason"))
            response.statusCode shouldBe HttpStatus.BAD_GATEWAY
        }

        // API-CN5
        it("returns 409 when already fully refunded") {
            every { issueCreditNoteUseCase.execute(1L, "FULL", "REFUND_TO_PAYMENT", null, "Reason") } returns IssueCreditNoteError.AlreadyFullyRefunded.left()
            val response = controller.issueCreditNote(1L, IssueCreditNoteRequest(type = "FULL", application = "REFUND_TO_PAYMENT", reason = "Reason"))
            response.statusCode shouldBe HttpStatus.CONFLICT
        }
    }

    describe("List Credit Notes API") {
        // API-LC1
        it("returns 200 with list") {
            val dto = CreditNoteDto(
                id = 1L, invoiceId = 1L, subscriptionId = 1L,
                amount = "49.99", currency = "USD", reason = "Refund",
                type = "FULL", application = "REFUND_TO_PAYMENT", status = "APPLIED",
                refundTransactionId = "ref_1", createdAt = now, updatedAt = now,
            )
            every { creditNoteListQueryUseCase.execute(1L) } returns listOf(dto).right()
            val response = controller.listCreditNotes(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-LC2
        it("returns 200 with empty list") {
            every { creditNoteListQueryUseCase.execute(1L) } returns emptyList<CreditNoteDto>().right()
            val response = controller.listCreditNotes(1L)
            response.statusCode shouldBe HttpStatus.OK
        }

        // API-LC3
        it("returns 400 for invalid invoice ID") {
            every { creditNoteListQueryUseCase.execute(-1L) } returns CreditNoteListQueryError.InvalidInput("invoiceId", "Must be positive").left()
            val response = controller.listCreditNotes(-1L)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }
})
