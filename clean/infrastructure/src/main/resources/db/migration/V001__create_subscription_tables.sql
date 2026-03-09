-- Plans table
CREATE TABLE plans (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL,
    base_price_amount DECIMAL(19, 4) NOT NULL,
    base_price_currency VARCHAR(3) NOT NULL,
    usage_limit INTEGER,
    features    TEXT NOT NULL,
    tier        VARCHAR(20) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

-- Subscriptions table
CREATE TABLE subscriptions (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         BIGINT NOT NULL,
    plan_id             BIGINT NOT NULL REFERENCES plans(id),
    status              VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end  TIMESTAMP WITH TIME ZONE NOT NULL,
    trial_start         TIMESTAMP WITH TIME ZONE,
    trial_end           TIMESTAMP WITH TIME ZONE,
    paused_at           TIMESTAMP WITH TIME ZONE,
    canceled_at         TIMESTAMP WITH TIME ZONE,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    grace_period_end    TIMESTAMP WITH TIME ZONE,
    pause_count_in_period INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_subscriptions_customer_id ON subscriptions(customer_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

-- Discounts table
CREATE TABLE discounts (
    id                BIGSERIAL PRIMARY KEY,
    subscription_id   BIGINT NOT NULL REFERENCES subscriptions(id),
    type              VARCHAR(20) NOT NULL,
    percentage_value  DECIMAL(5, 2),
    fixed_amount      DECIMAL(19, 4),
    fixed_currency    VARCHAR(3),
    duration_months   INTEGER,
    remaining_cycles  INTEGER,
    applied_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_discounts_subscription_id ON discounts(subscription_id);

-- Invoices table
CREATE TABLE invoices (
    id                    BIGSERIAL PRIMARY KEY,
    subscription_id       BIGINT NOT NULL REFERENCES subscriptions(id),
    subtotal_amount       DECIMAL(19, 4) NOT NULL,
    discount_amount       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total_amount          DECIMAL(19, 4) NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    due_date              DATE NOT NULL,
    paid_at               TIMESTAMP WITH TIME ZONE,
    payment_attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_invoices_subscription_id ON invoices(subscription_id);
CREATE INDEX idx_invoices_status ON invoices(status);

-- Invoice line items table
CREATE TABLE invoice_line_items (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id),
    description VARCHAR(500) NOT NULL,
    amount      DECIMAL(19, 4) NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    type        VARCHAR(30) NOT NULL
);

CREATE INDEX idx_invoice_line_items_invoice_id ON invoice_line_items(invoice_id);

-- Usage records table
CREATE TABLE usage_records (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    metric_name     VARCHAR(255) NOT NULL,
    quantity        INTEGER NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX idx_usage_records_subscription_id ON usage_records(subscription_id);
CREATE INDEX idx_usage_records_idempotency_key ON usage_records(idempotency_key);
CREATE INDEX idx_usage_records_period ON usage_records(subscription_id, recorded_at);
