-- ==============================================================================
-- V1__init_schema.sql
-- Kabadiwala Connect: Core Database Schema (SIH 26229)
-- ==============================================================================

-- 1. Waste Categories (Worker-Facing Taxonomy)
CREATE TABLE IF NOT EXISTS waste_categories (
    code VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. User Profiles (Synced / linked with Supabase Auth UUID)
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(100),
    phone_number VARCHAR(20),
    role VARCHAR(30) NOT NULL, -- COLLECTOR, RECYCLER, ADMIN
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Pricing Rates (Configurable price table per category in INR)
CREATE TABLE IF NOT EXISTS pricing_rates (
    id UUID PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL REFERENCES waste_categories(code),
    price_per_kg NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    effective_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Recyclers (Verified recycling partners and accepted materials)
CREATE TABLE IF NOT EXISTS recyclers (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES user_profiles(id),
    facility_name VARCHAR(150) NOT NULL,
    location_address TEXT,
    city VARCHAR(100),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    accepted_category_codes TEXT[] NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Material Lots (Central business entity managed by collector)
CREATE TABLE IF NOT EXISTS material_lots (
    id UUID PRIMARY KEY,
    collector_id UUID NOT NULL REFERENCES user_profiles(id),
    status VARCHAR(30) NOT NULL, -- CREATED, CLASSIFIED, PRICED, READY_FOR_HANDOVER, HANDED_OVER, COMPLETED, CANCELLED
    confirmed_category_code VARCHAR(50) REFERENCES waste_categories(code),
    classification_method VARCHAR(30), -- MANUAL, AI_CONFIRMED, AI_CORRECTED
    weight_kg NUMERIC(8, 3),
    price_per_kg NUMERIC(10, 2),
    estimated_price NUMERIC(12, 2),
    final_price NUMERIC(12, 2),
    image_url VARCHAR(500),
    image_storage_ref VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Classification Records (Provenance tracking for predictions & human confirmation)
CREATE TABLE IF NOT EXISTS classification_records (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL REFERENCES material_lots(id) ON DELETE CASCADE,
    predicted_class VARCHAR(100),
    confidence NUMERIC(5, 4),
    model_name VARCHAR(100),
    model_version VARCHAR(50),
    inference_latency_ms BIGINT,
    classification_method VARCHAR(30) NOT NULL,
    worker_confirmed_category_code VARCHAR(50) REFERENCES waste_categories(code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Handover Transactions (Transfer from collector to recycler)
CREATE TABLE IF NOT EXISTS handover_transactions (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL REFERENCES material_lots(id),
    collector_id UUID NOT NULL REFERENCES user_profiles(id),
    recycler_id UUID NOT NULL REFERENCES recyclers(id),
    agreed_weight_kg NUMERIC(8, 3) NOT NULL,
    agreed_price_per_kg NUMERIC(10, 2) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL, -- INITIATED, ACCEPTED, REJECTED, COMPLETED
    handover_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_material_lots_collector_id ON material_lots(collector_id);
CREATE INDEX IF NOT EXISTS idx_material_lots_status ON material_lots(status);
CREATE INDEX IF NOT EXISTS idx_material_lots_category ON material_lots(confirmed_category_code);
CREATE INDEX IF NOT EXISTS idx_classification_records_lot_id ON classification_records(lot_id);
CREATE INDEX IF NOT EXISTS idx_handover_transactions_lot_id ON handover_transactions(lot_id);
CREATE INDEX IF NOT EXISTS idx_handover_transactions_collector_id ON handover_transactions(collector_id);
CREATE INDEX IF NOT EXISTS idx_handover_transactions_recycler_id ON handover_transactions(recycler_id);
CREATE INDEX IF NOT EXISTS idx_pricing_rates_category ON pricing_rates(category_code, is_active);
