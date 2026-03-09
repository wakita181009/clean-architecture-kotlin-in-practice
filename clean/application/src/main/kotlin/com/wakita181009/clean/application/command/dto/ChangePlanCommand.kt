package com.wakita181009.clean.application.command.dto

data class ChangePlanCommand(
    val subscriptionId: Long,
    val newPlanId: Long,
)
