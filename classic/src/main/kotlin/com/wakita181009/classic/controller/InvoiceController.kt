package com.wakita181009.classic.controller

import com.wakita181009.classic.dto.CreditNoteResponse
import com.wakita181009.classic.dto.InvoiceResponse
import com.wakita181009.classic.dto.IssueCreditNoteRequest
import com.wakita181009.classic.service.CreditNoteService
import com.wakita181009.classic.service.InvoiceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invoices")
class InvoiceController(
    private val invoiceService: InvoiceService,
    private val creditNoteService: CreditNoteService,
) {
    @PostMapping("/{id}/pay")
    fun recoverPayment(
        @PathVariable id: Long,
    ): ResponseEntity<InvoiceResponse> {
        require(id > 0) { "Invoice ID must be positive" }
        val invoice = invoiceService.recoverPayment(id)
        return ResponseEntity.ok(InvoiceResponse.from(invoice))
    }

    @GetMapping
    fun listInvoices(
        @RequestParam subscriptionId: Long,
    ): ResponseEntity<List<InvoiceResponse>> {
        require(subscriptionId > 0) { "Subscription ID must be positive" }
        val invoices = invoiceService.listBySubscriptionId(subscriptionId)
        return ResponseEntity.ok(invoices.map { InvoiceResponse.from(it) })
    }

    // Phase 1 endpoints

    @PostMapping("/{id}/credit-notes")
    fun issueCreditNote(
        @PathVariable id: Long,
        @Valid @RequestBody request: IssueCreditNoteRequest,
    ): ResponseEntity<CreditNoteResponse> {
        require(id > 0) { "Invoice ID must be positive" }
        val creditNote =
            creditNoteService.issueCreditNote(
                invoiceId = id,
                type = request.type,
                application = request.application,
                amount = request.amount,
                reason = request.reason,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(CreditNoteResponse.from(creditNote))
    }

    @GetMapping("/{id}/credit-notes")
    fun listCreditNotes(
        @PathVariable id: Long,
    ): ResponseEntity<List<CreditNoteResponse>> {
        require(id > 0) { "Invoice ID must be positive" }
        val creditNotes = creditNoteService.listCreditNotes(id)
        return ResponseEntity.ok(creditNotes.map { CreditNoteResponse.from(it) })
    }
}
