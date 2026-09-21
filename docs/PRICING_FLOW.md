# Pricing Architecture & Flow — Kabadiwala Connect

**Project:** SIH 2026 Problem Statement 26229 — Kabadiwala Connect  
**Component:** Pricing Engine & Valuation Logic  
**Date:** September 2026  

---

## 1. Architectural Principle: ML vs. Pricing Separation

> [!IMPORTANT]
> **ML classification and pricing are strictly decoupled.**
> 
> * **The ML Model** identifies the physical object / waste category (e.g. `PCB`, `Smartphone`, `Battery`). It produces **zero currency values**.
> * **The Pricing Engine** determines reference market value in INR based on verified weight and category rate tables maintained in PostgreSQL.
> * If the ML prediction is wrong or low-confidence, the worker corrects the category to one of the 14 standard categories. The pricing engine automatically re-prices the lot using the corrected category rate.

```text
[ Physical Item ] 
       ↓
[ Camera Photo ] ──→ [ On-Device ONNX / ML ] ──→ Predicted Class: "PCB"
                                                        ↓
                                              [ Taxonomy Mapping ] ──→ Worker Category: "PCB"
                                                                               ↓
                                                    [ Worker Verification / Correction ]
                                                                               ↓
                                                [ Scale Weight: 4.500 kg ] ────┤
                                                                               ↓
                                                                  [ Pricing Service ]
                                                                 (Rate: ₹180.00 / kg)
                                                                               ↓
                                                                Estimated Total: ₹810.00
```

---

## 2. Where Reference Rates are Stored

* **Database Table:** `pricing_rates`
* **Schema Definition (`V1__init_schema.sql`):**
  ```sql
  CREATE TABLE pricing_rates (
      id UUID PRIMARY KEY,
      category_code VARCHAR(50) NOT NULL REFERENCES waste_categories(code),
      price_per_kg NUMERIC(10, 2) NOT NULL,
      currency VARCHAR(10) NOT NULL DEFAULT 'INR',
      effective_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      is_active BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
  ```
* **Java Entity:** `com.kabadiwala.backend.pricing.PricingRate`
* **Repository Query:**
  ```java
  Optional<PricingRate> findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc(String categoryCode);
  ```

---

## 3. Active Baseline Rates (Seeded in `V2__seed_taxonomy_and_rates.sql`)

| Category Code | Display Name | Baseline Price (₹ / kg) | Unit |
|---|---|:---:|:---:|
| `PCB` | PCB / Circuit Board | ₹180.00 | INR / kg |
| `CABLE_WIRE` | Cable / Wire | ₹160.00 | INR / kg |
| `PHONE_SMALL_ELECTRONICS` | Phone / Small Electronics | ₹150.00 | INR / kg |
| `STORAGE_DEVICE` | Storage Device (HDD, SSD) | ₹120.00 | INR / kg |
| `ELECTRONIC_COMPONENTS` | Electronic Components | ₹90.00 | INR / kg |
| `MOTOR_MECHANICAL` | Motor / Mechanical | ₹55.00 | INR / kg |
| `CHARGER_ADAPTER` | Charger / Adapter / Power Supply | ₹50.00 | INR / kg |
| `BATTERY` | Battery | ₹45.00 | INR / kg |
| `DISPLAY_SCREEN` | Display / Screen | ₹35.00 | INR / kg |
| `METAL_SCRAP` | Metal E-Waste / Scrap | ₹35.00 | INR / kg |
| `MIXED_EWASTE` | Mixed E-Waste | ₹30.00 | INR / kg |
| `OTHER_EWASTE` | Other E-Waste (incl. Laptop) | ₹25.00 | INR / kg |
| `PLASTIC_EWASTE` | Plastic E-Waste (Keyboards, Mice) | ₹18.00 | INR / kg |
| `NON_EWASTE` | Not E-Waste | ₹0.00 | INR / kg |

---

## 4. How Weight Affects Price

Calculation formula in `PricingService.java`:
$$\text{Estimated Total} = \text{PricePerKg} \times \text{WeightKg}$$

* **Precision:** Rounded to 2 decimal places using `RoundingMode.HALF_UP`.
* **Validation:** Weight must be strictly positive ($> 0.000$ kg), with minimum 1 gram ($0.001$ kg) enforced by DTO validation in `RecordWeightRequest`.
* **State Trigger:**
  * When `POST /api/v1/material-lots/{id}/weight` is called, the lot status transitions from `CLASSIFIED` to `PRICED`.
  * `MaterialLot.pricePerKg` is stamped with the current active rate.
  * `MaterialLot.estimatedPrice` is computed and persisted.
  * If weight is recorded *before* classification confirmation, confirmation will immediately trigger auto-pricing.

---

## 5. Pricing APIs

### 5.1 List All Active Rates
* **Endpoint:** `GET /api/v1/pricing/rates`
* **Auth:** Authenticated
* **Output:** Full rate sheet for display in collector and recycler rate tabs.

### 5.2 Dynamic Price Estimator (Ad-hoc Calculator)
* **Endpoint:** `GET /api/v1/pricing/estimate?categoryCode=PCB&weightKg=4.500`
* **Auth:** Authenticated
* **Output:**
  ```json
  {
    "success": true,
    "data": {
      "pricePerKg": 180.00,
      "estimatedTotal": 810.00
    }
  }
  ```

---

## 6. Location, Recycler Bids, & Transaction Settlement Audit

* **Do Location / City affect reference rates?**
  * **No.** In the current implementation, `pricing_rates` does not have a `city` or `region` column. Reference rates are uniform statewide/national benchmarks.
  * Recyclers have a `city` attribute, which is used for matching (`GET /api/v1/recyclers/match?city=Delhi`), but rates are standardized.
* **Do Recycler-Specific Offers / Bids exist?**
  * **No separate bidding engine exists.** When a collector initiates a transaction, the lot is offered at the standard reference price.
* **Does a Final Transaction Price exist?**
  * **Yes.** In `POST /api/v1/transactions/{id}/accept`, the recycler confirms the actual measured weight (`confirmedWeightKg`) and the negotiated/verified rate (`confirmedPricePerKg`).
  * The backend computes:
    $$\text{totalAmount} = \text{confirmedWeightKg} \times \text{confirmedPricePerKg}$$
  * This is persisted into `handover_transactions.total_amount` and updates `material_lots.final_price`.
  * If the recycler finds contaminated scrap or lower purity, they adjust `confirmedPricePerKg` during acceptance, and the settlement reflects that adjusted final amount.
