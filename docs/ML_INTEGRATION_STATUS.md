# ML Integration Status & Local ONNX Architecture — Kabadiwala Connect

**Project:** SIH 2026 Problem Statement 26229 — Kabadiwala Connect  
**Component:** ML E-Waste Classifier Integration  
**Status:** **Local ONNX Ingestion Contract Implemented & Verified**  
**Date:** September 2026  

---

## 1. Prototype Direction: Local On-Device ONNX Inference

> [!IMPORTANT]
> **No Separate Inference Server in the Final Frontend Flow.**
>
> The verified deployment target is **in-browser / on-device edge inference using ONNX Runtime Web / React Native ONNX**.
>
> * **Selected Edge Architecture:** `ShuffleNetV2-x1.0` (Exported ONNX size: **4.91 MB**, CPU Latency: **2.10 ms**, Test Accuracy: 83.77%, Macro-F1: 0.8232).
> * **Backup / Cloud Architecture:** `MobileNetV3-Large` (Exported ONNX size: **16.07 MB**, CPU Latency: **2.37 ms**, Test Accuracy: 84.66%, Macro-F1: 0.8467).
> * The frontend downloads the lightweight model file (`.onnx`) once and executes inference locally on the user's device via WebAssembly/WebGPU.

---

## 2. Implemented Local ONNX Metadata Ingestion Contract

The backend now directly accepts locally generated ONNX metadata on the standard confirmation endpoint:

### Endpoint
`POST /api/v1/material-lots/{id}/confirm-classification`

### Contract Specification (`ConfirmClassificationRequest`)

| Field | Type | Validation / Rules | Description |
|---|---|---|---|
| `confirmedCategoryCode` | `String` | **Required**, `@NotBlank`, max 50 chars | The worker-verified category code (one of the 14 worker categories). |
| `predictedClass` | `String` | Optional, max 100 chars (Alias: `"prediction"`) | Raw class name predicted by local ONNX model (e.g. `"PCB"`, `"Flat-Panel-Monitor"`). |
| `confidence` | `BigDecimal` | Optional, validated `0.0 <= confidence <= 1.0` | Softmax probability score produced by local ONNX model. |
| `modelName` | `String` | Optional, max 100 chars (Alias: `"model"`) | Model identifier, e.g. `"ShuffleNetV2-x1.0-onnx"` (defaults to `"local-onnx"` if omitted). |
| `modelVersion` | `String` | Optional, max 50 chars (Alias: `"version"`) | Model version string, e.g. `"v1.0"`. |
| `inferenceLatencyMs` | `Long` | Optional (Alias: `"latency_ms"`, `"latencyMs"`) | Inference runtime recorded locally in milliseconds. |
| `notes` | `String` | Optional, max 500 chars | Human verification or condition notes. |

---

## 3. Classification Modes: AI-Assisted vs. Manual

### Mode 1: AI-Assisted Classification (Local ONNX)
When the frontend runs inference on the device, it sends the predicted class and confidence:
```json
POST /api/v1/material-lots/3acbed44-08f5-4e8a-8112-66b14bd413f9/confirm-classification
Content-Type: application/json

{
  "confirmedCategoryCode": "PCB",
  "predictedClass": "PCB",
  "confidence": 0.9350,
  "modelName": "ShuffleNetV2-x1.0-onnx",
  "modelVersion": "v1.0",
  "inferenceLatencyMs": 22,
  "notes": "Verified by collector on device"
}
```
* **Backend Processing:**
  * Maps `predictedClass` through `TaxonomyMappingService.suggestWorkerCategory()`.
  * If `confirmedCategoryCode` matches suggested category: stamps `classificationMethod = AI_CONFIRMED`.
  * If worker changed the category: stamps `classificationMethod = AI_CORRECTED`.
  * Persists full provenance into `classification_records` table (`predicted_class`, `confidence`, `model_name`, `model_version`, `inference_latency_ms`).

### Mode 2: Manual Classification (Fallback / Worker Direct Choice)
When no AI prediction is available or worker opts for direct entry without running ONNX:
```json
POST /api/v1/material-lots/3acbed44-08f5-4e8a-8112-66b14bd413f9/confirm-classification
Content-Type: application/json

{
  "confirmedCategoryCode": "BATTERY",
  "notes": "Direct manual worker entry"
}
```
* **Backend Processing:**
  * Stamps `classificationMethod = MANUAL`.
  * Persists `ClassificationRecord` with `modelName = "manual"`, `predictedClass = null`, and **`confidence = null`**.
  * **No fake confidence values are invented.**

---

## 4. Dataset Classes vs. Worker Category Taxonomy

The ML model is trained on **10 prototype classes** (Dataset v1.0), whereas the recycling industry and backend use **14 worker-facing scrap categories**.

`TaxonomyMappingService.java` maps ML model predictions into the worker categories:

| ML Predicted Class (Dataset v1.0) | Worker Category Code | Worker Category Display Name | Baseline Rate (₹/kg) | Notes |
|---|---|---|:---:|---|
| `PCB` | `PCB` | PCB / Circuit Board | ₹180.00 | 1:1 direct mapping |
| `Battery` | `BATTERY` | Battery | ₹45.00 | 1:1 direct mapping |
| `Smartphone` | `PHONE_SMALL_ELECTRONICS` | Phone / Small Electronics | ₹150.00 | Grouped into small handheld devices |
| `HDD` | `STORAGE_DEVICE` | Storage Device | ₹120.00 | Grouped into magnetic/flash storage |
| `Flat-Panel-Monitor` | `DISPLAY_SCREEN` | Display / Screen | ₹35.00 | Grouped into displays |
| `Flat-Panel-TV` | `DISPLAY_SCREEN` | Display / Screen | ₹35.00 | Grouped into displays |
| `Computer-Keyboard` | `PLASTIC_EWASTE` | Plastic E-Waste | ₹18.00 | Valued as rigid electronic plastic |
| `Computer-Mouse` | `PLASTIC_EWASTE` | Plastic E-Waste | ₹18.00 | Valued as rigid electronic plastic |
| `Laptop` | `OTHER_EWASTE` | Other E-Waste | ₹25.00 | **Known limitation:** Scrapped as composite unit |
| `Non-E-Waste` | `NON_EWASTE` | Not E-Waste | ₹0.00 | Rejected from payout |
| *(Deferred: Power-Adapter)* | `CHARGER_ADAPTER` | Charger / Adapter / Power Supply | ₹50.00 | Worker manual selection |
| *(Deferred: Router)* | `OTHER_EWASTE` | Other E-Waste | ₹25.00 | Worker manual selection |
| *(Deferred: Cable/Wire)* | `CABLE_WIRE` | Cable / Wire | ₹160.00 | Worker manual selection |
| *(Deferred: Motor)* | `MOTOR_MECHANICAL` | Motor / Mechanical | ₹55.00 | Worker manual selection |
| *(Deferred: Discrete ICs)* | `ELECTRONIC_COMPONENTS` | Electronic Components | ₹90.00 | Worker manual selection |
| *(Deferred: Metal Scrap)* | `METAL_SCRAP` | Metal E-Waste / Scrap | ₹35.00 | Worker manual selection |
| *(Deferred: Mixed Debris)* | `MIXED_EWASTE` | Mixed E-Waste | ₹30.00 | Worker manual selection |

---

## 5. Pricing Decoupling Rule

> [!NOTE]
> The ML model identifies the material. The pricing engine determines reference market valuation ($Rate \times Weight$). No ML inference code calculates monetary values.
