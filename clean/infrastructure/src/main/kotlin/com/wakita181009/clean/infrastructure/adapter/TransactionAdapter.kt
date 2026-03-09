package com.wakita181009.clean.infrastructure.adapter

import com.wakita181009.clean.application.port.TransactionPort
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class TransactionAdapter(
    txManager: PlatformTransactionManager,
) : TransactionPort {
    private val txTemplate = TransactionTemplate(txManager)

    override fun <T> run(block: () -> T): T =
        txTemplate.execute { block() }
}
