package com.wakita181009.classic.controller

import com.wakita181009.classic.dto.CancelSubscriptionRequest
import com.wakita181009.classic.dto.ChangePlanRequest
import com.wakita181009.classic.dto.CreateSubscriptionRequest
import com.wakita181009.classic.dto.RecordUsageRequest
import com.wakita181009.classic.dto.SubscriptionResponse
import com.wakita181009.classic.dto.UsageRecordResponse
import com.wakita181009.classic.service.SubscriptionService
import com.wakita181009.classic.service.UsageService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/subscriptions")
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
    private val usageService: UsageService,
) {
    @PostMapping
    fun createSubscription(
        @Valid @RequestBody request: CreateSubscriptionRequest,
    ): ResponseEntity<SubscriptionResponse> {
        val subscription = subscriptionService.createSubscription(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(subscription))
    }

    @GetMapping("/{id}")
    fun getSubscription(
        @PathVariable id: Long,
    ): ResponseEntity<SubscriptionResponse> {
        require(id > 0) { "Subscription ID must be positive" }
        val subscription = subscriptionService.getSubscription(id)
        return ResponseEntity.ok(SubscriptionResponse.from(subscription))
    }

    @GetMapping
    fun listByCustomer(
        @RequestParam customerId: Long,
    ): ResponseEntity<List<SubscriptionResponse>> {
        require(customerId > 0) { "Customer ID must be positive" }
        val subscriptions = subscriptionService.listByCustomerId(customerId)
        return ResponseEntity.ok(subscriptions.map { SubscriptionResponse.from(it) })
    }

    @PutMapping("/{id}/plan")
    fun changePlan(
        @PathVariable id: Long,
        @Valid @RequestBody request: ChangePlanRequest,
    ): ResponseEntity<SubscriptionResponse> {
        require(id > 0) { "Subscription ID must be positive" }
        val subscription = subscriptionService.changePlan(id, request.newPlanId)
        return ResponseEntity.ok(SubscriptionResponse.from(subscription))
    }

    @PostMapping("/{id}/pause")
    fun pause(
        @PathVariable id: Long,
    ): ResponseEntity<SubscriptionResponse> {
        require(id > 0) { "Subscription ID must be positive" }
        val subscription = subscriptionService.pauseSubscription(id)
        return ResponseEntity.ok(SubscriptionResponse.from(subscription))
    }

    @PostMapping("/{id}/resume")
    fun resume(
        @PathVariable id: Long,
    ): ResponseEntity<SubscriptionResponse> {
        require(id > 0) { "Subscription ID must be positive" }
        val subscription = subscriptionService.resumeSubscription(id)
        return ResponseEntity.ok(SubscriptionResponse.from(subscription))
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
        @Valid @RequestBody request: CancelSubscriptionRequest,
    ): ResponseEntity<SubscriptionResponse> {
        require(id > 0) { "Subscription ID must be positive" }
        val subscription = subscriptionService.cancelSubscription(id, request.immediate)
        return ResponseEntity.ok(SubscriptionResponse.from(subscription))
    }

    @PostMapping("/{id}/usage")
    fun recordUsage(
        @PathVariable id: Long,
        @Valid @RequestBody request: RecordUsageRequest,
    ): ResponseEntity<UsageRecordResponse> {
        require(id > 0) { "Subscription ID must be positive" }
        val usage = usageService.recordUsage(id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(UsageRecordResponse.from(usage))
    }
}
