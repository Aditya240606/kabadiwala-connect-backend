-- ==============================================================================
-- V3__add_payment_method_and_lifecycle.sql
-- Kabadiwala Connect: Add payment_method column to handover_transactions
-- ==============================================================================

ALTER TABLE handover_transactions
ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30);
