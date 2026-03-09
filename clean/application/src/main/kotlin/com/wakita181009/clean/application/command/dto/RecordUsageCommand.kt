package com.wakita181009.clean.application.command.dto

data class RecordUsageCommand(
    val subscriptionId: Long,
    val metricName: String,
    val quantity: Int,
    val idempotencyKey: String,
)
