package com.wakita181009.clean.application.command.dto

data class CreateSubscriptionCommand(
    val customerId: Long,
    val planId: Long,
    val paymentMethod: String,
    val discountCode: String?,
    val seatCount: Int? = null,
)
