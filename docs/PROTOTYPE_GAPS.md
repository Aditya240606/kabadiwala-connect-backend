# Prototype Flow & Gaps Audit — Kabadiwala Connect

**Project:** SIH 2026 Problem Statement 26229 — Kabadiwala Connect  
**Scope:** Backend & Integration Implementation  
**Status:** **GAPS RESOLVED — Ready for Frontend Integration**  
**Date:** September 2026  

---

## 1. End-to-End Collector → Recycler Prototype Flow (Post-Implementation)

The full 16-step prototype lifecycle is now supported end-to-end:

| # | Prototype Flow Step | Status | Backing Endpoint / Entity | Audit Findings & Implemented Reality |
|---|---|:---:|---|---|
| 1 | **Create lot** | ✅ Fully supported | `POST /api/v1/material-lots` | Creates `MaterialLot` record with status `CREATED`. Collector ID automatically resolved from JWT. |
| 2 | **Upload photo** | ✅ Fully supported | `POST /api/v1/material-lots` (multipart `image`) | Uploads image via `StorageService`. Image URL is publicly viewable via `GET /api/v1/storage/files/**`. |
| 3 | **AI classification** | ✅ Fully supported | `POST /api/v1/material-lots/{id}/confirm-classification` | **Resolved:** Frontend executes lightweight ONNX inference locally on-device and sends `predictedClass`, `confidence`, `modelName`, `inferenceLatencyMs`. Backend audits provenance in `classification_records`. |
| 4 | **Confirm/correct material** | ✅ Fully supported | `POST /api/v1/material-lots/{id}/confirm-classification` | Accepts `confirmedCategoryCode`. Stamped as `AI_CONFIRMED` or `AI_CORRECTED` (or `MANUAL` without fake confidence if no prediction). Transitions lot to `CLASSIFIED`. |
| 5 | **Enter weight** | ✅ Fully supported | `POST /api/v1/material-lots/{id}/weight` | Accepts `weightKg` (validated 0.001 kg to 50,000 kg). |
| 6 | **Get reference price** | ✅ Fully supported | `GET /api/v1/pricing/rates` | Returns active rates in INR/kg for all 14 categories from `pricing_rates` table. |
| 7 | **Calculate estimated value** | ✅ Fully supported | Automated in `POST .../weight` & `GET /api/v1/pricing/estimate` | Automatically computes `pricePerKg * weightKg`. Sets lot status to `PRICED`. |
| 8 | **Find recycler** | ✅ Fully supported | `GET /api/v1/recyclers/match?categoryCode=...&city=...` | Filters active recyclers by `accepted_category_codes` array and optional city. |
| 9 | **Select recycler** | ✅ Fully supported | Client-side selection | Client selects recycler ID from match results for transaction initiation. |
| 10 | **Initiate transaction** | ✅ Fully supported | `POST /api/v1/transactions/initiate` | Requires lot in `READY_FOR_HANDOVER`. Creates `HandoverTransaction` with status `INITIATED`. Sets lot to `HANDED_OVER`. |
| 11 | **Recycler accepts** | ✅ Fully supported | `POST /api/v1/transactions/{id}/accept` | **Resolved:** Transitions `INITIATED -> ACCEPTED`. Marks lot ready for pickup/dock delivery. (Also supports one-step complete for fast demos). |
| 12 | **Collection / handover** | ✅ Fully supported | `POST /api/v1/transactions/{id}/collect` | **Resolved:** Transitions `ACCEPTED -> COLLECTED`. Recycler verifies scale weight and rate per kg. Computes `totalAmount`. |
| 13 | **Final price** | ✅ Fully supported | `HandoverTransaction.totalAmount` & `MaterialLot.finalPrice` | Automatically calculated as `confirmedWeightKg * confirmedPricePerKg` during collection/completion. Persisted in transaction and lot. |
| 14 | **Payment** | ✅ Fully supported | `POST /api/v1/transactions/{id}/complete` | **Resolved:** Records payment method (`CASH` or `DIGITAL`) in `handover_transactions.payment_method` (Flyway V3). |
| 15 | **Completed transaction** | ✅ Fully supported | `HandoverTransaction.status = COMPLETED`, `MaterialLot.status = COMPLETED` | Both lot and transaction transition to terminal `COMPLETED` state. |
| 16 | **History & Pending** | ✅ Fully supported | `GET /api/v1/transactions/recycler/pending`, `.../history`, `GET /api/v1/transactions` | **Resolved:** Dedicated recycler endpoints with strict tenant security. Collectors see collector history; recyclers see recycler history. |

---

## 2. Handover Transaction Lifecycle

```text
       INITIATED (Collector drafts & initiates handover to Recycler)
           │
           ├───────────────────────────────┐
           │ (POST /accept - no weight)    │ (POST /accept - with weight & price)
           ▼                               │ [Backward-compatible 1-step flow]
       ACCEPTED (Recycler accepts pickup)  │
           │                               │
           │ (POST /collect - weight & rate)
           ▼                               │
       COLLECTED (Verified on scale)       │
           │                               │
           │ (POST /complete - CASH/DIGITAL)
           ▼                               ▼
       COMPLETED ◄─────────────────────────┘
           ▲
           └── (Lot status: COMPLETED, finalPrice updated)
```
*(Also supports `POST /{id}/reject` at `INITIATED` or `ACCEPTED` $\rightarrow$ `REJECTED`, which returns lot back to `READY_FOR_HANDOVER`).*

---

## 3. Resolution of Identified Gaps

### Gap 1: Recycler Query APIs (RESOLVED)
* **Implemented:**
  * `GET /api/v1/transactions/recycler/pending` — Lists requests with status `INITIATED`.
  * `GET /api/v1/transactions/recycler/history` (and `/api/v1/transactions/recycler`) — Lists all requests assigned to authenticated recycler.
  * `GET /api/v1/transactions/{id}` — Full transaction detail with tenant security checks (verifies user is the collector, the assigned recycler facility owner, or admin).
  * `GET /api/v1/transactions` — Role-based router (returns collector transactions for collectors, recycler transactions for recyclers).

### Gap 2: Local ONNX Inference Metadata Ingestion (RESOLVED)
* **Implemented:**
  * `ConfirmClassificationRequest` now accepts optional `predictedClass`, `confidence`, `modelName`, `modelVersion`, `inferenceLatencyMs`.
  * Validates confidence range: $0.0 \le \text{confidence} \le 1.0$.
  * Sets `AI_CONFIRMED` when category matches prediction/mapping, or `AI_CORRECTED` when overridden by worker.
  * Persists full ML provenance into `classification_records`.
  * For manual classification (no AI fields), records `method = MANUAL` and `confidence = null` without fake confidence numbers.

### Gap 3: Believable Handover Lifecycle & Payment (RESOLVED)
* **Implemented:**
  * Added `COLLECTED` to `HandoverStatus` enum.
  * Created Flyway migration `V3__add_payment_method_and_lifecycle.sql` adding `payment_method VARCHAR(30)` to `handover_transactions`.
  * Added `POST /api/v1/transactions/{id}/accept` for initial acceptance (`INITIATED -> ACCEPTED`).
  * Added `POST /api/v1/transactions/{id}/collect` for physical weigh-in (`ACCEPTED -> COLLECTED`).
  * Added `POST /api/v1/transactions/{id}/complete` for payment record (`COLLECTED -> COMPLETED` with `paymentMethod: "CASH"|"DIGITAL"`).
  * Kept backward compatibility: calling `/accept` with weight and rate performs atomic 1-step completion.
