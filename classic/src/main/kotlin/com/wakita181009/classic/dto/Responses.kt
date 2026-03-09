package com.wakita181009.classic.dto

import com.wakita181009.classic.model.Discount
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceLineItem
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.UsageRecord
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class MoneyResponse(
    val amount: BigDecimal,
    val currency: String,
) {
    companion object {
        fun from(money: Money) = MoneyResponse(money.amount, money.currency.name)
    }
}

data class PlanResponse(
    val id: Long,
    val name: String,
    val tier: String,
    val billingInterval: String,
    val basePrice: MoneyResponse,
) {
    companion object {
        fun from(plan: Plan) =
            PlanResponse(
                id = plan.id,
                name = plan.name,
                tier = plan.tier.name,
                billingInterval = plan.billingInterval.name,
                basePrice = MoneyResponse.from(plan.basePrice),
            )
    }
}

data class DiscountResponse(
    val type: String,
    val value: BigDecimal,
    val remainingCycles: Int?,
) {
    companion object {
        fun from(discount: Discount?) =
            discount?.let {
                DiscountResponse(
                    type = it.type.name,
                    value = it.value,
                    remainingCycles = it.remainingCycles,
                )
            }
    }
}

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
        fun from(
            subscription: Subscription,
            discount: Discount? = null,
        ) = SubscriptionResponse(
            id = subscription.id,
            customerId = subscription.customerId,
            plan = PlanResponse.from(subscription.plan),
            status = subscription.status.name,
            currentPeriodStart = subscription.currentPeriodStart,
            currentPeriodEnd = subscription.currentPeriodEnd,
            trialEnd = subscription.trialEnd,
            pausedAt = subscription.pausedAt,
            canceledAt = subscription.canceledAt,
            cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
            discount = DiscountResponse.from(discount),
            createdAt = subscription.createdAt,
            updatedAt = subscription.updatedAt,
        )
    }
}

data class LineItemResponse(
    val description: String,
    val amount: MoneyResponse,
    val type: String,
) {
    companion object {
        fun from(item: InvoiceLineItem) =
            LineItemResponse(
                description = item.description,
                amount = MoneyResponse.from(item.amount),
                type = item.type.name,
            )
    }
}

data class InvoiceResponse(
    val id: Long,
    val subscriptionId: Long,
    val lineItems: List<LineItemResponse>,
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
        fun from(invoice: Invoice) =
            InvoiceResponse(
                id = invoice.id,
                subscriptionId = invoice.subscription.id,
                lineItems = invoice.lineItems.map { LineItemResponse.from(it) },
                subtotal = MoneyResponse.from(invoice.subtotal),
                discountAmount = MoneyResponse.from(invoice.discountAmount),
                total = MoneyResponse.from(invoice.total),
                status = invoice.status.name,
                dueDate = invoice.dueDate,
                paidAt = invoice.paidAt,
                paymentAttemptCount = invoice.paymentAttemptCount,
                createdAt = invoice.createdAt,
            )
    }
}

data class UsageRecordResponse(
    val id: Long,
    val subscriptionId: Long,
    val metricName: String,
    val quantity: Int,
    val recordedAt: Instant,
    val idempotencyKey: String,
) {
    companion object {
        fun from(record: UsageRecord) =
            UsageRecordResponse(
                id = record.id,
                subscriptionId = record.subscription.id,
                metricName = record.metricName,
                quantity = record.quantity,
                recordedAt = record.recordedAt,
                idempotencyKey = record.idempotencyKey,
            )
    }
}
