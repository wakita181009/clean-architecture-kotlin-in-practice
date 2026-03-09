package com.wakita181009.classic.exception

import org.springframework.http.HttpStatus

class PlanNotFoundException(
    id: Long,
) : ServiceException("Plan not found: $id", HttpStatus.NOT_FOUND)

class SubscriptionNotFoundException(
    id: Long,
) : ServiceException("Subscription not found: $id", HttpStatus.NOT_FOUND)

class InvoiceNotFoundException(
    id: Long,
) : ServiceException("Invoice not found: $id", HttpStatus.NOT_FOUND)

class InvalidStateTransitionException(
    from: String,
    to: String,
) : ServiceException("Cannot transition from $from to $to", HttpStatus.CONFLICT)

class BusinessRuleViolationException(
    message: String,
) : ServiceException(message, HttpStatus.CONFLICT)

class PaymentFailedException(
    message: String,
) : ServiceException(message, HttpStatus.BAD_GATEWAY)

class DuplicateSubscriptionException(
    customerId: Long,
) : ServiceException("Customer $customerId already has an active subscription", HttpStatus.CONFLICT)

class InvalidDiscountCodeException(
    code: String,
) : ServiceException("Invalid discount code: $code", HttpStatus.BAD_REQUEST)

class AddOnNotFoundException(
    id: Long,
) : ServiceException("Add-on not found: $id", HttpStatus.NOT_FOUND)

class SubscriptionAddOnNotFoundException(
    subscriptionId: Long,
    addOnId: Long,
) : ServiceException("Add-on $addOnId not attached to subscription $subscriptionId", HttpStatus.NOT_FOUND)

class CurrencyMismatchException(
    expected: String,
    actual: String,
) : ServiceException("Currency mismatch: expected $expected but got $actual", HttpStatus.CONFLICT)

class TierIncompatibilityException(
    tier: String,
    compatibleTiers: String,
) : ServiceException("Plan tier $tier is not compatible. Compatible tiers: $compatibleTiers", HttpStatus.CONFLICT)

class DuplicateAddOnException(
    addonId: Long,
) : ServiceException("Add-on $addonId is already attached to this subscription", HttpStatus.CONFLICT)

class AddOnLimitReachedException(
    limit: Int,
) : ServiceException("Maximum number of active add-ons ($limit) reached", HttpStatus.CONFLICT)

class NotPerSeatPlanException : ServiceException("Subscription is not on a per-seat plan", HttpStatus.CONFLICT)

class PerSeatAddOnOnNonPerSeatPlanException :
    ServiceException("PER_SEAT add-on can only be attached to per-seat plan subscriptions", HttpStatus.CONFLICT)

class SeatCountOutOfRangeException(
    message: String,
) : ServiceException(message, HttpStatus.CONFLICT)

class SameSeatCountException(
    current: Int,
) : ServiceException("New seat count is the same as current: $current", HttpStatus.CONFLICT)

class SeatCountRequiredException : ServiceException("Seat count is required for per-seat plans", HttpStatus.BAD_REQUEST)

class InvoiceNotPaidException(
    invoiceId: Long,
) : ServiceException("Invoice $invoiceId is not in PAID status", HttpStatus.CONFLICT)

class AlreadyFullyRefundedException(
    invoiceId: Long,
) : ServiceException("Invoice $invoiceId has already been fully refunded", HttpStatus.CONFLICT)

class CreditAmountExceedsRemainingException(
    remaining: String,
    requested: String,
) : ServiceException("Requested credit amount $requested exceeds remaining refundable amount $remaining", HttpStatus.CONFLICT)
