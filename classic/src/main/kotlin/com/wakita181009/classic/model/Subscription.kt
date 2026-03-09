package com.wakita181009.classic.model

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "subscriptions",
    indexes = [
        Index(name = "idx_subscriptions_customer_id", columnList = "customer_id"),
        Index(name = "idx_subscriptions_status", columnList = "status"),
    ],
)
class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "customer_id", nullable = false)
    val customerId: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    var plan: Plan,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.TRIAL,
    @Column(name = "current_period_start", nullable = false)
    var currentPeriodStart: Instant,
    @Column(name = "current_period_end", nullable = false)
    var currentPeriodEnd: Instant,
    @Column(name = "trial_start")
    val trialStart: Instant? = null,
    @Column(name = "trial_end")
    val trialEnd: Instant? = null,
    @Column(name = "paused_at")
    var pausedAt: Instant? = null,
    @Column(name = "canceled_at")
    var canceledAt: Instant? = null,
    @Column(name = "cancel_at_period_end", nullable = false)
    var cancelAtPeriodEnd: Boolean = false,
    @Column(name = "grace_period_end")
    var gracePeriodEnd: Instant? = null,
    @Column(name = "pause_count", nullable = false)
    var pauseCount: Int = 0,
    @Column(name = "seat_count")
    var seatCount: Int? = null,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "account_credit_balance_amount")),
        AttributeOverride(name = "currency", column = Column(name = "account_credit_balance_currency")),
    )
    var accountCreditBalance: Money = Money.zero(Money.Currency.USD),
    @OneToMany(mappedBy = "subscription")
    val subscriptionAddOns: MutableList<SubscriptionAddOn> = mutableListOf(),
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun transitionTo(newStatus: SubscriptionStatus) {
        check(status.canTransitionTo(newStatus)) {
            "Cannot transition from $status to $newStatus"
        }
        status = newStatus
    }
}
