package com.wakita181009.clean.application.port

interface TransactionPort {
    fun <T> run(block: () -> T): T
}
