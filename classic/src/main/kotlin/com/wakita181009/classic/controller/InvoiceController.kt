package com.wakita181009.classic.controller

import com.wakita181009.classic.dto.InvoiceResponse
import com.wakita181009.classic.service.InvoiceService
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
    private val invoiceService: InvoiceService,
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
}
