-- ==============================================================================
-- V2__seed_taxonomy_and_rates.sql
-- Seed 14 Worker-Facing Categories and Baseline Pricing Rates (INR)
-- ==============================================================================

-- Seed Waste Categories
INSERT INTO waste_categories (code, display_name, description, is_active) VALUES
    ('PCB', 'PCB / Circuit Board', 'Printed circuit boards, motherboards, green/blue logic boards', TRUE),
    ('CABLE_WIRE', 'Cable / Wire', 'Copper cables, power cords, wiring harnesses, ribbon cables', TRUE),
    ('BATTERY', 'Battery', 'Lithium-ion, lead-acid, NiMH laptop and phone batteries', TRUE),
    ('MOTOR_MECHANICAL', 'Motor / Mechanical', 'Small electric motors, DVD drive mechanisms, fan assemblies', TRUE),
    ('DISPLAY_SCREEN', 'Display / Screen', 'LCD, LED, OLED monitor screens and laptop panels', TRUE),
    ('STORAGE_DEVICE', 'Storage Device', 'Mechanical HDDs, SATA/NVMe SSDs, flash storage', TRUE),
    ('CHARGER_ADAPTER', 'Charger / Adapter / Power Supply', 'Wall adapters, laptop chargers, internal power supplies (SMPS)', TRUE),
    ('PHONE_SMALL_ELECTRONICS', 'Phone / Small Electronics', 'Smartphones, feature phones, calculators, small handheld electronics', TRUE),
    ('ELECTRONIC_COMPONENTS', 'Electronic Components', 'Capacitors, transformers, heat sinks, discrete IC chips', TRUE),
    ('PLASTIC_EWASTE', 'Plastic E-Waste', 'External printer casings, computer monitor shells, rigid electronic plastics', TRUE),
    ('METAL_SCRAP', 'Metal E-Waste / Scrap', 'Aluminum heat sinks, steel desktop chassis, server frames', TRUE),
    ('OTHER_EWASTE', 'Other E-Waste', 'Uncommon or unclassified electronic equipment', TRUE),
    ('MIXED_EWASTE', 'Mixed E-Waste', 'Unsorted combined electronic debris', TRUE),
    ('NON_EWASTE', 'Not E-Waste', 'Organic, paper, general municipal rubbish not eligible for e-waste payout', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Seed Baseline Pricing Rates (INR per kg)
INSERT INTO pricing_rates (id, category_code, price_per_kg, currency, effective_from, is_active) VALUES
    (gen_random_uuid(), 'PCB', 180.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'CABLE_WIRE', 160.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'BATTERY', 45.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'MOTOR_MECHANICAL', 55.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'DISPLAY_SCREEN', 35.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'STORAGE_DEVICE', 120.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'CHARGER_ADAPTER', 50.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'PHONE_SMALL_ELECTRONICS', 150.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'ELECTRONIC_COMPONENTS', 90.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'PLASTIC_EWASTE', 18.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'METAL_SCRAP', 35.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'OTHER_EWASTE', 25.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'MIXED_EWASTE', 30.00, 'INR', CURRENT_TIMESTAMP, TRUE),
    (gen_random_uuid(), 'NON_EWASTE', 0.00, 'INR', CURRENT_TIMESTAMP, TRUE);
