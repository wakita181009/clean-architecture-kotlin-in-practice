-- Phase 1: Add-on Management, Seat Management, Credit Notes

-- Extend plans with seat-related fields
ALTER TABLE plans ADD COLUMN per_seat_pricing BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE plans ADD COLUMN min_seats INTEGER NOT NULL DEFAULT 1;
ALTER TABLE plans ADD COLUMN max_seats INTEGER;

-- Extend subscriptions with seat count and account credit balance
ALTER TABLE subscriptions ADD COLUMN seat_count INTEGER;
ALTER TABLE subscriptions ADD COLUMN account_credit_balance_amount DECIMAL(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN account_credit_balance_currency VARCHAR(3) NOT NULL DEFAULT 'USD';

-- New: addons (master data, manual ID like plans)
CREATE TABLE addons (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_amount DECIMAL(19, 4) NOT NULL,
    price_currency VARCHAR(3) NOT NULL,
    billing_type VARCHAR(50) NOT NULL,
    compatible_tiers TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- New: subscription_addons
CREATE TABLE subscription_addons (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    addon_id BIGINT NOT NULL REFERENCES addons(id),
    quantity INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    attached_at TIMESTAMP WITH TIME ZONE NOT NULL,
    detached_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_subscription_addons_subscription_id ON subscription_addons(subscription_id);
CREATE INDEX idx_subscription_addons_addon_id ON subscription_addons(addon_id);

-- New: credit_notes
CREATE TABLE credit_notes (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    subscription_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    application VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ISSUED',
    refund_transaction_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_credit_notes_invoice_id ON credit_notes(invoice_id);
CREATE INDEX idx_credit_notes_subscription_id ON credit_notes(subscription_id);
