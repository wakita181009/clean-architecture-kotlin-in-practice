-- Add payment_method column to subscriptions table
ALTER TABLE subscriptions
    ADD COLUMN payment_method VARCHAR(255);
