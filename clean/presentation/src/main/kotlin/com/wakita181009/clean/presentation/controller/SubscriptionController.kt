package com.wakita181009.clean.presentation.controller

import com.wakita181009.clean.application.command.dto.CancelSubscriptionCommand
import com.wakita181009.clean.application.command.dto.ChangePlanCommand
import com.wakita181009.clean.application.command.dto.CreateSubscriptionCommand
import com.wakita181009.clean.application.command.dto.RecordUsageCommand
import com.wakita181009.clean.application.command.error.CancelSubscriptionError
import com.wakita181009.clean.application.command.error.PauseSubscriptionError
import com.wakita181009.clean.application.command.error.PlanChangeError
import com.wakita181009.clean.application.command.error.RecordUsageError
import com.wakita181009.clean.application.command.error.ResumeSubscriptionError
import com.wakita181009.clean.application.command.error.SubscriptionCreateError
import com.wakita181009.clean.application.command.usecase.CancelSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.PauseSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.PlanChangeUseCase
import com.wakita181009.clean.application.command.usecase.RecordUsageUseCase
import com.wakita181009.clean.application.command.usecase.ResumeSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.SubscriptionCreateUseCase
import com.wakita181009.clean.application.query.error.SubscriptionFindByIdQueryError
import com.wakita181009.clean.application.query.error.SubscriptionListByCustomerQueryError
import com.wakita181009.clean.application.query.usecase.SubscriptionFindByIdQueryUseCase
import com.wakita181009.clean.application.query.usecase.SubscriptionListByCustomerQueryUseCase
import com.wakita181009.clean.presentation.dto.CancelSubscriptionRequest
import com.wakita181009.clean.presentation.dto.ChangePlanRequest
import com.wakita181009.clean.presentation.dto.CreateSubscriptionRequest
import com.wakita181009.clean.presentation.dto.ErrorResponse
import com.wakita181009.clean.presentation.dto.RecordUsageRequest
import com.wakita181009.clean.presentation.dto.SubscriptionResponse
import com.wakita181009.clean.presentation.dto.UsageRecordResponse
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
    private val subscriptionCreateUseCase: SubscriptionCreateUseCase,
    private val planChangeUseCase: PlanChangeUseCase,
    private val pauseSubscriptionUseCase: PauseSubscriptionUseCase,
    private val resumeSubscriptionUseCase: ResumeSubscriptionUseCase,
    private val cancelSubscriptionUseCase: CancelSubscriptionUseCase,
    private val recordUsageUseCase: RecordUsageUseCase,
    private val subscriptionFindByIdQueryUseCase: SubscriptionFindByIdQueryUseCase,
    private val subscriptionListByCustomerQueryUseCase: SubscriptionListByCustomerQueryUseCase,
) {

    @PostMapping
    fun create(@RequestBody request: CreateSubscriptionRequest): ResponseEntity<*> =
        subscriptionCreateUseCase.execute(
            CreateSubscriptionCommand(
                customerId = request.customerId,
                planId = request.planId,
                paymentMethod = request.paymentMethod,
                discountCode = request.discountCode,
            ),
        ).fold(
            ifLeft = { error ->
                when (error) {
                    is SubscriptionCreateError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is SubscriptionCreateError.PlanNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is SubscriptionCreateError.AlreadySubscribed -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is SubscriptionCreateError.InvalidDiscountCode -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is SubscriptionCreateError.Domain -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                    is SubscriptionCreateError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { subscription ->
                ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(subscription))
            },
        )

    @PutMapping("/{id}/plan")
    fun changePlan(@PathVariable id: Long, @RequestBody request: ChangePlanRequest): ResponseEntity<*> =
        planChangeUseCase.execute(
            ChangePlanCommand(subscriptionId = id, newPlanId = request.newPlanId),
        ).fold(
            ifLeft = { error ->
                when (error) {
                    is PlanChangeError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is PlanChangeError.SubscriptionNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is PlanChangeError.NotActive -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is PlanChangeError.SamePlan -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is PlanChangeError.PlanNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is PlanChangeError.CurrencyMismatch -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is PlanChangeError.PaymentFailed -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse(error.message))
                    is PlanChangeError.Domain -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                    is PlanChangeError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { subscription ->
                ResponseEntity.ok(SubscriptionResponse.from(subscription))
            },
        )

    @PostMapping("/{id}/pause")
    fun pause(@PathVariable id: Long): ResponseEntity<*> =
        pauseSubscriptionUseCase.execute(id).fold(
            ifLeft = { error ->
                when (error) {
                    is PauseSubscriptionError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is PauseSubscriptionError.SubscriptionNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is PauseSubscriptionError.NotActive -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is PauseSubscriptionError.PauseLimitReached -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is PauseSubscriptionError.Domain -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                    is PauseSubscriptionError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { subscription ->
                ResponseEntity.ok(SubscriptionResponse.from(subscription))
            },
        )

    @PostMapping("/{id}/resume")
    fun resume(@PathVariable id: Long): ResponseEntity<*> =
        resumeSubscriptionUseCase.execute(id).fold(
            ifLeft = { error ->
                when (error) {
                    is ResumeSubscriptionError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is ResumeSubscriptionError.SubscriptionNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is ResumeSubscriptionError.NotPaused -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is ResumeSubscriptionError.Domain -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                    is ResumeSubscriptionError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { subscription ->
                ResponseEntity.ok(SubscriptionResponse.from(subscription))
            },
        )

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: Long, @RequestBody request: CancelSubscriptionRequest): ResponseEntity<*> =
        cancelSubscriptionUseCase.execute(
            CancelSubscriptionCommand(subscriptionId = id, immediate = request.immediate),
        ).fold(
            ifLeft = { error ->
                when (error) {
                    is CancelSubscriptionError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is CancelSubscriptionError.SubscriptionNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is CancelSubscriptionError.AlreadyTerminal -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is CancelSubscriptionError.CannotEndOfPeriodForPaused -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is CancelSubscriptionError.Domain -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                    is CancelSubscriptionError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { subscription ->
                ResponseEntity.ok(SubscriptionResponse.from(subscription))
            },
        )

    @PostMapping("/{id}/usage")
    fun recordUsage(@PathVariable id: Long, @RequestBody request: RecordUsageRequest): ResponseEntity<*> =
        recordUsageUseCase.execute(
            RecordUsageCommand(
                subscriptionId = id,
                metricName = request.metricName,
                quantity = request.quantity,
                idempotencyKey = request.idempotencyKey,
            ),
        ).fold(
            ifLeft = { error ->
                when (error) {
                    is RecordUsageError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is RecordUsageError.SubscriptionNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is RecordUsageError.NotActive -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is RecordUsageError.UsageLimitExceeded -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.message))
                    is RecordUsageError.Domain -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                    is RecordUsageError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { usageRecord ->
                ResponseEntity.status(HttpStatus.CREATED).body(UsageRecordResponse.from(usageRecord))
            },
        )

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<*> =
        subscriptionFindByIdQueryUseCase.execute(id).fold(
            ifLeft = { error ->
                when (error) {
                    is SubscriptionFindByIdQueryError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is SubscriptionFindByIdQueryError.NotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(error.message))
                    is SubscriptionFindByIdQueryError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { dto ->
                ResponseEntity.ok(SubscriptionResponse.from(dto))
            },
        )

    @GetMapping
    fun listByCustomer(@RequestParam customerId: Long): ResponseEntity<*> =
        subscriptionListByCustomerQueryUseCase.execute(customerId).fold(
            ifLeft = { error ->
                when (error) {
                    is SubscriptionListByCustomerQueryError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is SubscriptionListByCustomerQueryError.Internal -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(error.message))
                }
            },
            ifRight = { dtos ->
                ResponseEntity.ok(dtos.map { SubscriptionResponse.from(it) })
            },
        )
}
