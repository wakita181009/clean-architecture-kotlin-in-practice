package com.wakita181009.classic.controller

import com.ninjasquad.springmockk.MockkBean
import com.wakita181009.classic.exception.AlreadyFullyRefundedException
import com.wakita181009.classic.exception.InvoiceNotPaidException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.CreditApplication
import com.wakita181009.classic.model.CreditNote
import com.wakita181009.classic.model.CreditNoteStatus
import com.wakita181009.classic.model.CreditNoteType
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.service.CreditNoteService
import com.wakita181009.classic.service.InvoiceService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@WebMvcTest(InvoiceController::class)
class InvoiceControllerPhase1Test {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var invoiceService: InvoiceService

    @MockkBean
    lateinit var creditNoteService: CreditNoteService

    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")

    private fun sampleSubscription() =
        Subscription(
            id = 1L,
            customerId = 1L,
            plan =
                Plan(
                    id = 1L,
                    name = "Pro",
                    billingInterval = BillingInterval.MONTHLY,
                    basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
                    tier = PlanTier.PROFESSIONAL,
                    features = setOf("feature1"),
                ),
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = fixedInstant,
            currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
        )

    private fun sampleInvoice() =
        Invoice(
            id = 1L,
            subscription = sampleSubscription(),
            subtotal = Money(BigDecimal("49.99"), Money.Currency.USD),
            discountAmount = Money(BigDecimal("0.00"), Money.Currency.USD),
            total = Money(BigDecimal("49.99"), Money.Currency.USD),
            status = InvoiceStatus.PAID,
            dueDate = LocalDate.of(2025, 1, 1),
            paidAt = fixedInstant,
        )

    private fun sampleCreditNote(status: CreditNoteStatus = CreditNoteStatus.APPLIED) =
        CreditNote(
            id = 1L,
            invoice = sampleInvoice(),
            subscription = sampleSubscription(),
            amount = Money(BigDecimal("49.99"), Money.Currency.USD),
            reason = "Customer request",
            type = CreditNoteType.FULL,
            application = CreditApplication.REFUND_TO_PAYMENT,
            status = status,
            refundTransactionId = "ref-tx-1",
        )

    // API-CN1: Full refund to payment
    @Test
    fun `POST credit-notes returns 201 for full refund`() {
        every {
            creditNoteService.issueCreditNote(
                invoiceId = 1L,
                type = CreditNoteType.FULL,
                application = CreditApplication.REFUND_TO_PAYMENT,
                amount = null,
                reason = "Customer request",
            )
        } returns sampleCreditNote()

        mockMvc
            .perform(
                post("/api/invoices/1/credit-notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"FULL","application":"REFUND_TO_PAYMENT","reason":"Customer request"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("APPLIED"))
    }

    // API-CN2: Partial as account credit
    @Test
    fun `POST credit-notes returns 201 for partial refund`() {
        val partialCreditNote =
            CreditNote(
                id = 2L,
                invoice = sampleInvoice(),
                subscription = sampleSubscription(),
                amount = Money(BigDecimal("20.00"), Money.Currency.USD),
                reason = "Partial refund",
                type = CreditNoteType.PARTIAL,
                application = CreditApplication.ACCOUNT_CREDIT,
                status = CreditNoteStatus.APPLIED,
            )

        every {
            creditNoteService.issueCreditNote(
                invoiceId = 1L,
                type = CreditNoteType.PARTIAL,
                application = CreditApplication.ACCOUNT_CREDIT,
                amount = BigDecimal("20.00"),
                reason = "Partial refund",
            )
        } returns partialCreditNote

        mockMvc
            .perform(
                post("/api/invoices/1/credit-notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"PARTIAL","application":"ACCOUNT_CREDIT","amount":20.00,"reason":"Partial refund"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("APPLIED"))
    }

    // API-CN3: Invoice not Paid
    @Test
    fun `POST credit-notes returns 409 when invoice not paid`() {
        every {
            creditNoteService.issueCreditNote(
                invoiceId = 1L,
                type = any(),
                application = any(),
                amount = any(),
                reason = any(),
            )
        } throws InvoiceNotPaidException(1L)

        mockMvc
            .perform(
                post("/api/invoices/1/credit-notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"FULL","application":"REFUND_TO_PAYMENT","reason":"test"}"""),
            ).andExpect(status().isConflict)
    }

    // API-CN4: Gateway refund fails
    @Test
    fun `POST credit-notes returns 502 when gateway fails`() {
        every {
            creditNoteService.issueCreditNote(
                invoiceId = 1L,
                type = any(),
                application = any(),
                amount = any(),
                reason = any(),
            )
        } throws PaymentFailedException("Refund failed")

        mockMvc
            .perform(
                post("/api/invoices/1/credit-notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"FULL","application":"REFUND_TO_PAYMENT","reason":"test"}"""),
            ).andExpect(status().isBadGateway)
    }

    // API-CN5: Already fully refunded
    @Test
    fun `POST credit-notes returns 409 when already fully refunded`() {
        every {
            creditNoteService.issueCreditNote(
                invoiceId = 1L,
                type = any(),
                application = any(),
                amount = any(),
                reason = any(),
            )
        } throws AlreadyFullyRefundedException(1L)

        mockMvc
            .perform(
                post("/api/invoices/1/credit-notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"FULL","application":"REFUND_TO_PAYMENT","reason":"test"}"""),
            ).andExpect(status().isConflict)
    }

    // API-LC1: List credit notes
    @Test
    fun `GET credit-notes returns 200 with list`() {
        every { creditNoteService.listCreditNotes(1L) } returns listOf(sampleCreditNote())

        mockMvc
            .perform(get("/api/invoices/1/credit-notes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
    }

    // API-LC2: Empty list
    @Test
    fun `GET credit-notes returns 200 with empty list`() {
        every { creditNoteService.listCreditNotes(1L) } returns emptyList()

        mockMvc
            .perform(get("/api/invoices/1/credit-notes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // API-LC3: Invalid invoice ID
    @Test
    fun `GET credit-notes returns 400 for negative invoice ID`() {
        mockMvc
            .perform(get("/api/invoices/-1/credit-notes"))
            .andExpect(status().isBadRequest)
    }
}
