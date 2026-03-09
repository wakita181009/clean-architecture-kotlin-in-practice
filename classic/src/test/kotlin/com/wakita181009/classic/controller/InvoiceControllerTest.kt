package com.wakita181009.classic.controller

import com.ninjasquad.springmockk.MockkBean
import com.wakita181009.classic.exception.BusinessRuleViolationException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.model.BillingInterval
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
class InvoiceControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var invoiceService: InvoiceService

    @MockkBean
    lateinit var creditNoteService: CreditNoteService

    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")

    private fun sampleInvoice(status: InvoiceStatus = InvoiceStatus.PAID) =
        Invoice(
            id = 1L,
            subscription =
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
                            active = true,
                            features = setOf("feature1"),
                        ),
                    status = SubscriptionStatus.ACTIVE,
                    currentPeriodStart = fixedInstant,
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
                ),
            subtotal = Money(BigDecimal("49.99"), Money.Currency.USD),
            discountAmount = Money(BigDecimal("0.00"), Money.Currency.USD),
            total = Money(BigDecimal("49.99"), Money.Currency.USD),
            status = status,
            dueDate = LocalDate.of(2025, 1, 1),
            paidAt = fixedInstant,
        )

    // API-RP1: Successful recovery
    @Test
    fun `POST pay returns 200 on successful recovery`() {
        every { invoiceService.recoverPayment(1L) } returns sampleInvoice(InvoiceStatus.PAID)

        mockMvc
            .perform(post("/api/invoices/1/pay"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAID"))
    }

    // API-RP2: Grace expired
    @Test
    fun `POST pay returns 409 when grace period expired`() {
        every { invoiceService.recoverPayment(1L) } throws BusinessRuleViolationException("Grace period expired")

        mockMvc
            .perform(post("/api/invoices/1/pay"))
            .andExpect(status().isConflict)
    }

    // API-RP3: Gateway failure
    @Test
    fun `POST pay returns 502 when gateway fails`() {
        every { invoiceService.recoverPayment(1L) } throws PaymentFailedException("Payment gateway error")

        mockMvc
            .perform(post("/api/invoices/1/pay"))
            .andExpect(status().isBadGateway)
    }

    // API-G4: List invoices
    @Test
    fun `GET invoices by subscriptionId returns 200`() {
        every { invoiceService.listBySubscriptionId(1L) } returns listOf(sampleInvoice())

        mockMvc
            .perform(get("/api/invoices").param("subscriptionId", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
    }
}
