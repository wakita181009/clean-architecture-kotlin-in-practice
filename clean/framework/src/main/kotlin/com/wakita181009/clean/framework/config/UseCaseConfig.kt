package com.wakita181009.clean.framework.config

import com.wakita181009.clean.application.command.port.DiscountCodePort
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.PlanQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.command.port.UsageQueryPort
import com.wakita181009.clean.application.command.usecase.CancelSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.CancelSubscriptionUseCaseImpl
import com.wakita181009.clean.application.command.usecase.PauseSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.PauseSubscriptionUseCaseImpl
import com.wakita181009.clean.application.command.usecase.PlanChangeUseCase
import com.wakita181009.clean.application.command.usecase.PlanChangeUseCaseImpl
import com.wakita181009.clean.application.command.usecase.ProcessRenewalUseCase
import com.wakita181009.clean.application.command.usecase.ProcessRenewalUseCaseImpl
import com.wakita181009.clean.application.command.usecase.RecordUsageUseCase
import com.wakita181009.clean.application.command.usecase.RecordUsageUseCaseImpl
import com.wakita181009.clean.application.command.usecase.RecoverPaymentUseCase
import com.wakita181009.clean.application.command.usecase.RecoverPaymentUseCaseImpl
import com.wakita181009.clean.application.command.usecase.ResumeSubscriptionUseCase
import com.wakita181009.clean.application.command.usecase.ResumeSubscriptionUseCaseImpl
import com.wakita181009.clean.application.command.usecase.SubscriptionCreateUseCase
import com.wakita181009.clean.application.command.usecase.SubscriptionCreateUseCaseImpl
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.application.query.repository.InvoiceQueryRepository
import com.wakita181009.clean.application.query.repository.SubscriptionQueryRepository
import com.wakita181009.clean.application.query.usecase.InvoiceListBySubscriptionQueryUseCase
import com.wakita181009.clean.application.query.usecase.InvoiceListBySubscriptionQueryUseCaseImpl
import com.wakita181009.clean.application.query.usecase.SubscriptionFindByIdQueryUseCase
import com.wakita181009.clean.application.query.usecase.SubscriptionFindByIdQueryUseCaseImpl
import com.wakita181009.clean.application.query.usecase.SubscriptionListByCustomerQueryUseCase
import com.wakita181009.clean.application.query.usecase.SubscriptionListByCustomerQueryUseCaseImpl
import com.wakita181009.clean.domain.repository.DiscountRepository
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import com.wakita181009.clean.domain.repository.UsageRecordRepository
import com.wakita181009.clean.domain.service.ProrationDomainService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfig {

    // --- Command Use Cases ---

    @Bean
    fun subscriptionCreateUseCase(
        subscriptionRepository: SubscriptionRepository,
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        planQueryPort: PlanQueryPort,
        discountCodePort: DiscountCodePort,
        discountRepository: DiscountRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): SubscriptionCreateUseCase = SubscriptionCreateUseCaseImpl(
        subscriptionRepository = subscriptionRepository,
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        planQueryPort = planQueryPort,
        discountCodePort = discountCodePort,
        discountRepository = discountRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    @Bean
    fun planChangeUseCase(
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        planQueryPort: PlanQueryPort,
        paymentGatewayPort: PaymentGatewayPort,
        prorationDomainService: ProrationDomainService,
        subscriptionRepository: SubscriptionRepository,
        invoiceRepository: InvoiceRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): PlanChangeUseCase = PlanChangeUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        planQueryPort = planQueryPort,
        paymentGatewayPort = paymentGatewayPort,
        prorationDomainService = prorationDomainService,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    @Bean
    fun pauseSubscriptionUseCase(
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        subscriptionRepository: SubscriptionRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): PauseSubscriptionUseCase = PauseSubscriptionUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        subscriptionRepository = subscriptionRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    @Bean
    fun resumeSubscriptionUseCase(
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        subscriptionRepository: SubscriptionRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): ResumeSubscriptionUseCase = ResumeSubscriptionUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        subscriptionRepository = subscriptionRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    @Bean
    fun cancelSubscriptionUseCase(
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        invoiceCommandQueryPort: InvoiceCommandQueryPort,
        subscriptionRepository: SubscriptionRepository,
        invoiceRepository: InvoiceRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): CancelSubscriptionUseCase = CancelSubscriptionUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        invoiceCommandQueryPort = invoiceCommandQueryPort,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    @Bean
    fun processRenewalUseCase(
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        usageQueryPort: UsageQueryPort,
        paymentGatewayPort: PaymentGatewayPort,
        subscriptionRepository: SubscriptionRepository,
        invoiceRepository: InvoiceRepository,
        discountRepository: DiscountRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): ProcessRenewalUseCase = ProcessRenewalUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        usageQueryPort = usageQueryPort,
        paymentGatewayPort = paymentGatewayPort,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        discountRepository = discountRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    @Bean
    fun recordUsageUseCase(
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        usageQueryPort: UsageQueryPort,
        usageRecordRepository: UsageRecordRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): RecordUsageUseCase = RecordUsageUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        usageQueryPort = usageQueryPort,
        usageRecordRepository = usageRecordRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    @Bean
    fun recoverPaymentUseCase(
        invoiceCommandQueryPort: InvoiceCommandQueryPort,
        subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
        paymentGatewayPort: PaymentGatewayPort,
        invoiceRepository: InvoiceRepository,
        subscriptionRepository: SubscriptionRepository,
        clockPort: ClockPort,
        transactionPort: TransactionPort,
    ): RecoverPaymentUseCase = RecoverPaymentUseCaseImpl(
        invoiceCommandQueryPort = invoiceCommandQueryPort,
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        paymentGatewayPort = paymentGatewayPort,
        invoiceRepository = invoiceRepository,
        subscriptionRepository = subscriptionRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    // --- Query Use Cases ---

    @Bean
    fun subscriptionFindByIdQueryUseCase(
        subscriptionQueryRepository: SubscriptionQueryRepository,
    ): SubscriptionFindByIdQueryUseCase = SubscriptionFindByIdQueryUseCaseImpl(
        subscriptionQueryRepository = subscriptionQueryRepository,
    )

    @Bean
    fun subscriptionListByCustomerQueryUseCase(
        subscriptionQueryRepository: SubscriptionQueryRepository,
    ): SubscriptionListByCustomerQueryUseCase = SubscriptionListByCustomerQueryUseCaseImpl(
        subscriptionQueryRepository = subscriptionQueryRepository,
    )

    @Bean
    fun invoiceListBySubscriptionQueryUseCase(
        invoiceQueryRepository: InvoiceQueryRepository,
    ): InvoiceListBySubscriptionQueryUseCase = InvoiceListBySubscriptionQueryUseCaseImpl(
        invoiceQueryRepository = invoiceQueryRepository,
    )
}
