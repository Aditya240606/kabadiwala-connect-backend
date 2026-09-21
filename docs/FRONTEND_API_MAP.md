# Frontend API Map — Kabadiwala Connect

**Target System:** Kabadiwala Connect Backend (SIH 2026 Problem Statement 26229)  
**Base URL:** `http://localhost:8080` (or configured environment host)  
**API Version:** `v1`  
**Authentication Scheme:** Bearer JWT (Supabase Auth token passed via `Authorization: Bearer <token>` header)

---

## 1. Overview & Authentication Rules

* **Public Endpoints (No Auth Required):**
  * `GET /api/v1/categories` — Waste taxonomy list
  * `GET /api/v1/storage/files/**` — Uploaded waste images (for browser `<img>` elements)
  * `GET /swagger-ui/**`, `GET /api-docs/**` — OpenAPI specification
* **Collector Endpoints:**
  * `POST /api/v1/material-lots` (Alias: `/api/v1/lots`)
  * `GET /api/v1/material-lots`
  * `GET /api/v1/material-lots/{id}`
  * `POST /api/v1/material-lots/{id}/confirm-classification` (Accepts local ONNX inference metadata)
  * `POST /api/v1/material-lots/{id}/weight`
  * `POST /api/v1/material-lots/{id}/ready`
  * `POST /api/v1/transactions/initiate`
  * `GET /api/v1/transactions` (Returns collector history)
* **Recycler Endpoints:**
  * `GET /api/v1/transactions/recycler/pending` — Incoming requests waiting for action
  * `GET /api/v1/transactions/recycler/history` (Alias: `/api/v1/transactions/recycler`) — All facility transactions
  * `GET /api/v1/transactions/{id}` — Full transaction details (authorized participants only)
  * `POST /api/v1/transactions/{id}/accept` — Accept request (`INITIATED -> ACCEPTED`, or one-step completion)
  * `POST /api/v1/transactions/{id}/collect` — Record verified weight/rate (`ACCEPTED -> COLLECTED`)
  * `POST /api/v1/transactions/{id}/complete` (Alias: `/{id}/pay`) — Record payment (`COLLECTED -> COMPLETED`)
  * `POST /api/v1/transactions/{id}/reject` — Reject request (`INITIATED/ACCEPTED -> REJECTED`)
* **Shared Reference Endpoints:**
  * `GET /api/v1/pricing/rates`
  * `GET /api/v1/pricing/estimate`
  * `GET /api/v1/recyclers/match`

---

## 2. Collector APIs

### 2.1 Get Material Categories
* **Method:** `GET`
* **Path:** `/api/v1/categories`
* **Auth Required:** No (Public)
* **Used By:** Collector (Manual category selector, fallback selection, taxonomy inspection)
* **Response:** List of 14 worker-facing waste categories (`PCB`, `BATTERY`, `CABLE_WIRE`, etc.).

---

### 2.2 Create Material Lot
* **Method:** `POST`
* **Path:** `/api/v1/material-lots` (Alias: `/api/v1/lots`)
* **Auth Required:** Yes (`ROLE_COLLECTOR` or `ROLE_ADMIN`)
* **Used By:** Collector (Lot drafting with optional photo upload and optional initial category)
* **Request:** `multipart/form-data` or `application/json`
  * Part `request` (`CreateMaterialLotRequest`, optional JSON):
    ```json
    {
      "initialCategoryCode": "PCB",
      "notes": "Collected from industrial estate"
    }
    ```
  * Part `image` (`MultipartFile`, optional): Binary photo upload.
* **Response (201 Created):** `MaterialLotDto` with status `CREATED` (or `CLASSIFIED` if category was provided).

---

### 2.3 List Collector Lots
* **Method:** `GET`
* **Path:** `/api/v1/material-lots` (Alias: `/api/v1/lots`)
* **Auth Required:** Yes (`ROLE_COLLECTOR` or `ROLE_ADMIN`)
* **Used By:** Collector (Dashboard, active lots screen, lot history)
* **Request:** Optional query params: `?page=0&size=20&sort=createdAt,desc`
* **Response (200 OK):** Paginated `Page<MaterialLotDto>`.

---

### 2.4 Get Lot Details
* **Method:** `GET`
* **Path:** `/api/v1/material-lots/{id}` (Alias: `/api/v1/lots/{id}`)
* **Auth Required:** Yes (`ROLE_COLLECTOR` owner or `ROLE_ADMIN`)
* **Response (200 OK):** Single `MaterialLotDto`.

---

### 2.5 Confirm / Correct Classification (Local ONNX Supported)
* **Method:** `POST`
* **Path:** `/api/v1/material-lots/{id}/confirm-classification`
* **Auth Required:** Yes (`ROLE_COLLECTOR` owner or `ROLE_ADMIN`)
* **Used By:** Collector (Submits result of local on-device ONNX inference or manual worker selection)
* **Request:** `application/json`

#### Option A: AI-Assisted (Local ONNX Inference)
```json
{
  "confirmedCategoryCode": "PCB",
  "predictedClass": "PCB",
  "confidence": 0.9350,
  "modelName": "ShuffleNetV2-x1.0-onnx",
  "modelVersion": "v1.0",
  "inferenceLatencyMs": 22,
  "notes": "Verified dual-layer board"
}
```
*(Aliases supported: `"prediction"` for `predictedClass`, `"model"` for `modelName`, `"latency_ms"` for `inferenceLatencyMs`).*  
*(Confidence is validated: `0.0 <= confidence <= 1.0`).*

#### Option B: Pure Manual Selection (No AI Prediction)
```json
{
  "confirmedCategoryCode": "BATTERY",
  "notes": "Manual selection by worker"
}
```
* **Response (200 OK):** Updated `MaterialLotDto` with `status: "CLASSIFIED"` and `classificationMethod: "AI_CONFIRMED"` (or `"AI_CORRECTED"` or `"MANUAL"`). Full provenance is recorded in `classificationRecord`.

---

### 2.6 Record Measured Weight
* **Method:** `POST`
* **Path:** `/api/v1/material-lots/{id}/weight` (Alias: `/{id}/record-weight`)
* **Auth Required:** Yes (`ROLE_COLLECTOR` owner or `ROLE_ADMIN`)
* **Used By:** Collector (Scale reading in kg)
* **Request:**
```json
{
  "weightKg": 4.500
}
```
* **Response (200 OK):** Returns updated `MaterialLotDto`. Status transitions to `PRICED`, with `pricePerKg` and `estimatedPrice` populated automatically from database rate table.

---

### 2.7 Mark Lot Ready for Handover
* **Method:** `POST`
* **Path:** `/api/v1/material-lots/{id}/ready` (Alias: `/{id}/ready-for-handover`)
* **Auth Required:** Yes (`ROLE_COLLECTOR` owner or `ROLE_ADMIN`)
* **Response (200 OK):** Returns updated `MaterialLotDto` with status `READY_FOR_HANDOVER`.

---

### 2.8 Initiate Handover Transaction
* **Method:** `POST`
* **Path:** `/api/v1/transactions/initiate` (Alias: `/api/v1/transactions`)
* **Auth Required:** Yes (`ROLE_COLLECTOR` owner of lot)
* **Request:**
```json
{
  "lotId": "c240175f-13f0-42a9-a0ab-d22dcfabf851",
  "recyclerId": "166c9d78-9bbc-45a7-9c56-a1ff7dbde2c2",
  "notes": "Delivering to facility gate B"
}
```
* **Response (201 Created):**
```json
{
  "success": true,
  "message": "Handover initiated",
  "data": {
    "id": "4d22eded-2678-4b1c-8a48-1c868276abee",
    "lotId": "c240175f-13f0-42a9-a0ab-d22dcfabf851",
    "collectorId": "e0287586-c677-4102-adab-89c2a87abf86",
    "recyclerId": "166c9d78-9bbc-45a7-9c56-a1ff7dbde2c2",
    "agreedWeightKg": 4.5,
    "agreedPricePerKg": 180.00,
    "totalAmount": 810.00,
    "status": "INITIATED",
    "paymentMethod": null,
    "handoverNotes": "Delivering to facility gate B",
    "createdAt": "2026-09-21T18:15:00Z",
    "completedAt": null
  }
}
```

---

### 2.9 Get Collector Transactions (History)
* **Method:** `GET`
* **Path:** `/api/v1/transactions`
* **Auth Required:** Yes (Authenticated collector)
* **Response (200 OK):** Paginated `Page<HandoverTransactionDto>`.

---

## 3. Recycler APIs

### 3.1 Get Pending Requests for Recycler
* **Method:** `GET`
* **Path:** `/api/v1/transactions/recycler/pending`
* **Auth Required:** Yes (`ROLE_RECYCLER` or `ROLE_ADMIN`)
* **Used By:** Recycler (Dashboard incoming queue)
* **Query Params:** `?page=0&size=20&sort=createdAt,desc`
* **Response (200 OK):**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": "4d22eded-2678-4b1c-8a48-1c868276abee",
        "lotId": "c240175f-13f0-42a9-a0ab-d22dcfabf851",
        "collectorId": "e0287586-c677-4102-adab-89c2a87abf86",
        "recyclerId": "166c9d78-9bbc-45a7-9c56-a1ff7dbde2c2",
        "agreedWeightKg": 4.5,
        "agreedPricePerKg": 180.00,
        "totalAmount": 810.00,
        "status": "INITIATED",
        "paymentMethod": null,
        "handoverNotes": "Delivering to facility gate B",
        "createdAt": "2026-09-21T18:15:00Z",
        "completedAt": null
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 1
  }
}
```

---

### 3.2 Get Recycler Transaction History
* **Method:** `GET`
* **Path:** `/api/v1/transactions/recycler/history` (Alias: `/api/v1/transactions/recycler`)
* **Auth Required:** Yes (`ROLE_RECYCLER` or `ROLE_ADMIN`)
* **Used By:** Recycler (Transaction history tab)
* **Response (200 OK):** Paginated `Page<HandoverTransactionDto>` of all transactions assigned to this recycler facility.

---

### 3.3 Get Transaction Details
* **Method:** `GET`
* **Path:** `/api/v1/transactions/{id}`
* **Auth Required:** Yes (Authorized participant: the assigned recycler facility owner, the initiating collector, or admin)
* **Used By:** Recycler & Collector (Detail modal, inspection screen)
* **Response (200 OK):** Single `HandoverTransactionDto`.
* **Errors:**
  * `403 FORBIDDEN`: If the user is not the collector, assigned recycler, or admin.
  * `404 NOT_FOUND`: Transaction ID does not exist.

---

### 3.4 Accept Handover Request
* **Method:** `POST`
* **Path:** `/api/v1/transactions/{id}/accept`
* **Auth Required:** Yes (Assigned recycler or admin)
* **Used By:** Recycler (Accepts the pickup/handover request)
* **Request Body (Optional):**
```json
{
  "notes": "Driver dispatched for collection"
}
```
*Note: If `confirmedWeightKg` and `confirmedPricePerKg` are also supplied in the request body, the transaction completes in one atomic step for backward compatibility.*
* **Response (200 OK):** Returns `HandoverTransactionDto` with `status: "ACCEPTED"`.

---

### 3.5 Record Material Collection
* **Method:** `POST`
* **Path:** `/api/v1/transactions/{id}/collect`
* **Auth Required:** Yes (Assigned recycler or admin)
* **Used By:** Recycler (Verifies physical weight on calibrated scale and confirmed rate per kg)
* **Request:**
```json
{
  "confirmedWeightKg": 4.800,
  "confirmedPricePerKg": 180.00,
  "notes": "Weighed at weighbridge bay 2"
}
```
* **Response (200 OK):** Returns `HandoverTransactionDto` with `status: "COLLECTED"`, updated `agreedWeightKg: 4.8`, and re-calculated `totalAmount: 864.00`.

---

### 3.6 Complete Transaction with Payment
* **Method:** `POST`
* **Path:** `/api/v1/transactions/{id}/complete` (Alias: `/api/v1/transactions/{id}/pay`)
* **Auth Required:** Yes (Assigned recycler or admin)
* **Used By:** Recycler (Finalizes handover, records payment method, and closes transaction)
* **Request:**
```json
{
  "paymentMethod": "CASH",  // "CASH" or "DIGITAL"
  "notes": "Cash handed over to collector"
}
```
* **Response (200 OK):** Returns `HandoverTransactionDto` with `status: "COMPLETED"`, `paymentMethod: "CASH"`, `completedAt: "2026-09-21T18:30:00Z"`. Material lot is also marked `COMPLETED` with `finalPrice = totalAmount`.

---

### 3.7 Reject Handover
* **Method:** `POST`
* **Path:** `/api/v1/transactions/{id}/reject`
* **Auth Required:** Yes (Assigned recycler or admin)
* **Request:** Optional query param: `?reason=Contaminated+material`
* **Response (200 OK):** Returns `HandoverTransactionDto` with `status: "REJECTED"`. Material lot reverts to `READY_FOR_HANDOVER` so collector can re-route.

---

## 4. Shared Reference APIs

### 4.1 Get Reference Rates
* **Method:** `GET`
* **Path:** `/api/v1/pricing/rates`
* **Auth Required:** Yes (Authenticated)
* **Response:** Active price sheet in INR/kg for all 14 categories.

### 4.2 Estimate Price
* **Method:** `GET`
* **Path:** `/api/v1/pricing/estimate?categoryCode=PCB&weightKg=4.500`
* **Auth Required:** Yes (Authenticated)
* **Response:** Calculated `pricePerKg` and `estimatedTotal`.

### 4.3 Match Recyclers
* **Method:** `GET`
* **Path:** `/api/v1/recyclers/match?categoryCode=PCB&city=Delhi`
* **Auth Required:** Yes (Authenticated)
* **Response:** Matching active facilities accepting the category in the designated city.

### 4.4 Static Waste Image Serving
* **Method:** `GET`
* **Path:** `/api/v1/storage/files/{filename}`
* **Auth Required:** No (Public)
* **Response:** Binary image for HTML `<img>` elements.
