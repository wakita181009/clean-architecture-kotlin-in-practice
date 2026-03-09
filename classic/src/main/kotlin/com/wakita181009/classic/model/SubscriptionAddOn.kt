package com.wakita181009.classic.model

import jakarta.persistence.Column
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
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "subscription_addons",
    indexes = [
        Index(name = "idx_subscription_addons_sub_id", columnList = "subscription_id"),
    ],
)
class SubscriptionAddOn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    val subscription: Subscription,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addon_id", nullable = false)
    val addOn: AddOn,
    @Column(nullable = false)
    var quantity: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionAddOnStatus = SubscriptionAddOnStatus.ACTIVE,
    @Column(name = "attached_at", nullable = false)
    val attachedAt: Instant,
    @Column(name = "detached_at")
    var detachedAt: Instant? = null,
) {
    fun transitionTo(newStatus: SubscriptionAddOnStatus) {
        check(status.canTransitionTo(newStatus)) {
            "Cannot transition SubscriptionAddOn from $status to $newStatus"
        }
        status = newStatus
    }
}
