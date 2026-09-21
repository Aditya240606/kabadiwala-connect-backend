# Kabadiwala Connect — Backend Service

**SIH 2026 — Problem Statement 26229**  
A digital platform empowering informal waste collectors ("kabadiwalas") across India to efficiently aggregate, identify, weigh, price, and hand over electronic waste to verified recycling facilities.

---

## 1. Technology Stack

* **Language**: Java 21 LTS
* **Framework**: Spring Boot 3.3.4 (Modular Monolith)
* **Build Tool**: Apache Maven
* **Database**: PostgreSQL (Supabase) with Flyway migrations
* **Authentication**: Supabase Auth with stateless JWT token validation
* **Object Storage**: Supabase Storage (`ewaste-images`) with local filesystem fallback
* **API Documentation**: SpringDoc OpenAPI 3.0 / Swagger UI
* **ML Inference Integration**: REST client adapter connecting to Python FastAPI

---

## 2. Environment Variables

Create a `backend/.env` file (copied from `backend/.env.example`) or export these variables in your shell.

| Variable | Description | Default / Example | Required for Runtime |
| :--- | :--- | :--- | :---: |
| `SUPABASE_DB_URL` | PostgreSQL JDBC Connection String | `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require` | **YES** |
| `SUPABASE_DB_USERNAME` | Database username | `postgres` | **YES** |
| `SUPABASE_DB_PASSWORD` | Database password | `<your-supabase-db-password>` | **YES** |
| `SUPABASE_URL` | Supabase project API gateway | `https://<project-ref>.supabase.co` | **YES** |
| `SUPABASE_SERVICE_ROLE_KEY` | Server-side secret key for storage uploads | `<your-service-role-key>` | **YES** (if cloud storage) |
| `SUPABASE_STORAGE_BUCKET` | Storage bucket for waste photos | `ewaste-images` | NO (default: `ewaste-images`) |
| `USE_LOCAL_STORAGE` | Enable local filesystem storage fallback | `true` (dev) / `false` (prod) | NO |
| `LOCAL_STORAGE_DIR` | Directory for local file uploads | `./storage/uploads` | NO |
| `ML_SERVICE_URL` | Base URL of FastAPI ML service | `http://localhost:8000` | **YES** |
| `ML_SERVICE_ENABLED` | Enable/disable ML calls (falls back to manual) | `true` | NO |

---

## 3. Local Startup & Verification

### Step 1: Run Development Mock ML Service
A zero-dependency HTTP server mimicking the FastAPI ML contract is included in the backend directory:
```bash
python mock_ml_service.py 8000
```
*Health check*: `GET http://localhost:8000/health`  
*Prediction endpoint*: `POST http://localhost:8000/api/v1/predict`

### Step 2: Run All Tests (Zero Cloud Dependencies Required)
The test suite executes against an in-memory test database with 100% isolation:
```bash
mvn test
```
*Status: 58/58 tests passing (security, domain lifecycle, pricing, ML client fallback, local ONNX metadata ingestion, negative/security invariants, and end-to-end flows).*

### Step 3: Start Spring Boot
```bash
mvn spring-boot:run
```

### Step 4: Access Swagger UI
Open in your browser:
* **Interactive Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI JSON specification**: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

---

## 4. Worker Taxonomy (14 Categories)

The 14 canonical worker-facing categories are seeded in Flyway migration `V2__seed_taxonomy_and_rates.sql`:

1. `PCB` — PCB / Circuit Board (₹180/kg)
2. `CABLE_WIRE` — Cable / Wire (₹160/kg)
3. `BATTERY` — Battery (₹45/kg)
4. `MOTOR_MECHANICAL` — Motor / Mechanical (₹55/kg)
5. `DISPLAY_SCREEN` — Display / Screen (₹35/kg)
6. `STORAGE_DEVICE` — Storage Device (₹120/kg)
7. `CHARGER_ADAPTER` — Charger / Adapter / Power Supply (₹50/kg)
8. `PHONE_SMALL_ELECTRONICS` — Phone / Small Electronics (₹150/kg)
9. `ELECTRONIC_COMPONENTS` — Electronic Components (₹90/kg)
10. `PLASTIC_EWASTE` — Plastic E-Waste (₹18/kg)
11. `METAL_SCRAP` — Metal E-Waste / Scrap (₹35/kg)
12. `OTHER_EWASTE` — Other E-Waste (₹25/kg)
13. `MIXED_EWASTE` — Mixed E-Waste (₹30/kg)
14. `NON_EWASTE` — Not E-Waste (₹0/kg)

*Note: For the prototype, whole complex devices such as `Laptop` and `Router` from the ML model map to `OTHER_EWASTE` in `TaxonomyMappingService`. This is a documented prototype limitation to be revisited post-model experimentation.*

---

## 5. Endpoints Summary

- `GET /api/v1/categories` — Public taxonomy inspection
- `POST /api/v1/lots` or `/api/v1/material-lots` — Create material lot (`COLLECTOR`)
- `GET /api/v1/lots/{id}` — Retrieve lot details (Owner/`ADMIN`)
- `POST /api/v1/lots/{id}/classify` — Trigger AI inference (`COLLECTOR`)
- `POST /api/v1/lots/{id}/confirm-classification` — Confirm/correct category with local ONNX metadata (`COLLECTOR`)
- `POST /api/v1/lots/{id}/weight` — Record weight & auto-price (`COLLECTOR`)
- `POST /api/v1/lots/{id}/ready` — Stage for handover (`COLLECTOR`)
- `GET /api/v1/pricing/effective` — Active rate card
- `GET /api/v1/recyclers/match` — Match verified recyclers
- `POST /api/v1/transactions/initiate` — Initiate handover (`COLLECTOR`)
- `GET /api/v1/transactions` — List user's transactions (Collector or Recycler)
- `GET /api/v1/transactions/{id}` — Retrieve transaction details (Collector/Recycler/`ADMIN`)
- `GET /api/v1/transactions/recycler/pending` — List pending requests for recycler (`RECYCLER`)
- `GET /api/v1/transactions/recycler/history` — List completed recycler transactions (`RECYCLER`)
- `POST /api/v1/transactions/{id}/accept` — Accept handover request (`RECYCLER`)
- `POST /api/v1/transactions/{id}/collect` — Record collection & verified weight (`RECYCLER`)
- `POST /api/v1/transactions/{id}/complete` — Record payment & complete transaction (`RECYCLER`)
- `POST /api/v1/transactions/{id}/reject` — Reject handover (`RECYCLER`)
- `POST /api/v1/storage/upload` — Upload lot image
