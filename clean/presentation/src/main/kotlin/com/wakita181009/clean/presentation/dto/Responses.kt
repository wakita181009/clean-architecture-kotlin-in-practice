package com.wakita181009.clean.presentation.dto

import com.wakita181009.clean.application.query.dto.InvoiceDto
import com.wakita181009.clean.application.query.dto.InvoiceLineItemDto
import com.wakita181009.clean.application.query.dto.SubscriptionDto
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.UsageRecord
import java.time.Instant
import java.time.LocalDate

data class SubscriptionResponse(
    val id: Long,
    val customerId: Long,
    val plan: PlanResponse,
    val status: String,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant,
    val trialEnd: Instant?,
    val pausedAt: Instant?,
    val canceledAt: Instant?,
    val cancelAtPeriodEnd: Boolean,
    val discount: DiscountResponse?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(subscription: Subscription): SubscriptionResponse = SubscriptionResponse(
            id = subscription.id!!.value,
            customerId = subscription.customerId.value,
            plan = PlanResponse(
                id = subscription.plan.id.value,
                name = subscription.plan.name,
                tier = subscription.plan.tier.name,
                billingInterval = subscription.plan.billingInterval.name,
                basePrice = MoneyResponse(
                    amount = subscription.plan.basePrice.amount,
                    currency = subscription.plan.basePrice.currency.name,
                ),
            ),
            status = subscription.status.name,
            currentPeriodStart = subscription.currentPeriodStart,
            currentPeriodEnd = subscription.currentPeriodEnd,
            trialEnd = subscription.trialEnd,
            pausedAt = subscription.pausedAt,
            canceledAt = subscription.canceledAt,
            cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
            discount = null,
            createdAt = subscription.createdAt,
            updatedAt = subscription.updatedAt,
        )

        fun from(dto: SubscriptionDto): SubscriptionResponse = SubscriptionResponse(
            id = dto.id,
            customerId = dto.customerId,
            plan = PlanResponse(
                id = dto.planId,
                name = dto.planName,
                tier = dto.planTier,
                billingInterval = dto.planBillingInterval,
                basePrice = MoneyResponse(
                    amount = java.math.BigDecimal(dto.planBasePriceAmount),
                    currency = dto.planBasePriceCurrency,
                ),
            ),
            status = dto.status,
            currentPeriodStart = dto.currentPeriodStart,
            currentPeriodEnd = dto.currentPeriodEnd,
            trialEnd = dto.trialEnd,
            pausedAt = dto.pausedAt,
            canceledAt = dto.canceledAt,
            cancelAtPeriodEnd = dto.cancelAtPeriodEnd,
            discount = dto.discountType?.let { discountType ->
                DiscountResponse(
                    type = discountType,
                    value = dto.discountValue ?: "0",
                    remainingCycles = dto.discountRemainingCycles,
                )
            },
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}

data class PlanResponse(
    val id: Long,
    val name: String,
    val tier: String,
    val billingInterval: String,
    val basePrice: MoneyResponse,
)

data class MoneyResponse(
    val amount: java.math.BigDecimal,
    val currency: String,
)

data class DiscountResponse(
    val type: String,
    val value: String,
    val remainingCycles: Int?,
)

data class InvoiceResponse(
    val id: Long,
    val subscriptionId: Long,
    val lineItems: List<InvoiceLineItemResponse>,
    val subtotal: MoneyResponse,
    val discountAmount: MoneyResponse,
    val total: MoneyResponse,
    val status: String,
    val dueDate: LocalDate,
    val paidAt: Instant?,
    val paymentAttemptCount: Int,
    val createdAt: Instant,
) {
    companion object {
        fun from(invoice: com.wakita181009.clean.domain.model.Invoice): InvoiceResponse = InvoiceResponse(
            id = invoice.id!!.value,
            subscriptionId = invoice.subscriptionId.value,
            lineItems = invoice.lineItems.map { li ->
                InvoiceLineItemResponse(
                    description = li.description,
                    amount = MoneyResponse(li.amount.amount, li.amount.currency.name),
                    type = li.type.name,
                )
            },
            subtotal = MoneyResponse(invoice.subtotal.amount, invoice.subtotal.currency.name),
            discountAmount = MoneyResponse(invoice.discountAmount.amount, invoice.discountAmount.currency.name),
            total = MoneyResponse(invoice.total.amount, invoice.total.currency.name),
            status = invoice.status.name,
            dueDate = invoice.dueDate,
            paidAt = invoice.paidAt,
            paymentAttemptCount = invoice.paymentAttemptCount,
            createdAt = invoice.createdAt,
        )

        fun from(dto: InvoiceDto): InvoiceResponse = InvoiceResponse(
            id = dto.id,
            subscriptionId = dto.subscriptionId,
            lineItems = dto.lineItems.map { li ->
                InvoiceLineItemResponse(
                    description = li.description,
                    amount = MoneyResponse(java.math.BigDecimal(li.amount), li.currency),
                    type = li.type,
                )
            },
            subtotal = MoneyResponse(java.math.BigDecimal(dto.subtotalAmount), dto.subtotalCurrency),
            discountAmount = MoneyResponse(java.math.BigDecimal(dto.discountAmount), dto.subtotalCurrency),
            total = MoneyResponse(java.math.BigDecimal(dto.totalAmount), dto.totalCurrency),
            status = dto.status,
            dueDate = dto.dueDate,
            paidAt = dto.paidAt,
            paymentAttemptCount = dto.paymentAttemptCount,
            createdAt = dto.createdAt,
        )
    }
}

data class InvoiceLineItemResponse(
    val description: String,
    val amount: MoneyResponse,
    val type: String,
)

data class UsageRecordResponse(
    val id: Long,
    val subscriptionId: Long,
    val metricName: String,
    val quantity: Int,
    val recordedAt: Instant,
    val idempotencyKey: String,
) {
    companion object {
        fun from(record: UsageRecord): UsageRecordResponse = UsageRecordResponse(
            id = record.id!!.value,
            subscriptionId = record.subscriptionId.value,
            metricName = record.metricName.value,
            quantity = record.quantity,
            recordedAt = record.recordedAt,
            idempotencyKey = record.idempotencyKey.value,
        )
    }
}

data class ErrorResponse(
    val message: String,
)
