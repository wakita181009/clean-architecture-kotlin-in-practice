package com.wakita181009.clean.application.command.dto

data class CancelSubscriptionCommand(
    val subscriptionId: Long,
    val immediate: Boolean,
)
