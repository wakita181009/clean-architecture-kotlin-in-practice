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
