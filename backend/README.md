# TruthGuard Backend Service

The Spring Boot backend acts as the central orchestrator, document metadata repository, and deterministic rule resolution engine of the TruthGuard system.

## Responsibilities
- Orchestrating question answering workflows across retrieval, AI inspection, and conflict resolution.
- Managing document entities and revision versions with immutability and provenance tracking.
- Distinguishing strictly between **upload timestamp**, **effective date**, and **explicit supersession**.
- Validating duplicate uploads using cryptographic content hashes (SHA-256).
- Executing explicit, deterministic resolution rules (arbitration of conflicting evidence, freshness ranking, claim validation).
- Enforcing the core guarantee: **never silently combine contradictory claims or claim that an action succeeded when it has not.**
- Exposing REST APIs for document ingestion and retrieval.

## Technology Stack
- **Java**: 17+ LTS
- **Framework**: Spring Boot 3.3.4
- **Persistence**: Spring Data JPA, Hibernate ORM
- **Database Migrations**: Flyway
- **Database Driver**: PostgreSQL 16 Driver (production/runtime), H2 in-memory (test scope)
- **Build Tool**: Apache Maven
- **Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc

---

## API Endpoints

### 1. Health Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Application health status and service metadata |
| `GET` | `/actuator/health` | Spring Boot Actuator health endpoint |

### 2. Document & Version Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/documents` | Create a logical document or policy container |
| `GET` | `/api/v1/documents` | List all documents ordered by creation time |
| `GET` | `/api/v1/documents/{documentId}` | Retrieve a document and its version count |
| `POST` | `/api/v1/documents/{documentId}/versions` | Register a new version/revision with SHA-256 content hashing |
| `GET` | `/api/v1/documents/{documentId}/versions` | List versions for a document (supports `sortBy=uploadTimestamp` or `effectiveDate`, `direction=asc|desc`) |
| `GET` | `/api/v1/documents/{documentId}/versions/{versionId}` | Retrieve version metadata, provenance, and supersession state |
| `POST` | `/api/v1/documents/{documentId}/versions/{versionId}/supersede` | Explicitly establish that a newer version supersedes this version |

---

## Sample API Requests & Responses

### 1. Create Document
**Request:**
```http
POST /api/v1/documents HTTP/1.1
Content-Type: application/json

{
  "title": "Remote Work and Data Security Policy",
  "sourceType": "POLICY",
  "description": "Enterprise guidelines for remote work and security compliance"
}
```

**Response (HTTP 201 Created):**
```json
{
  "id": "8836a6a7-2e4b-4a40-8155-992d15899e8f",
  "title": "Remote Work and Data Security Policy",
  "sourceType": "POLICY",
  "description": "Enterprise guidelines for remote work and security compliance",
  "createdAt": "2026-10-20T14:23:31.120Z",
  "updatedAt": "2026-10-20T14:23:31.120Z",
  "versionCount": 0
}
```

### 2. Register Document Version
**Request:**
```http
POST /api/v1/documents/8836a6a7-2e4b-4a40-8155-992d15899e8f/versions HTTP/1.1
Content-Type: application/json

{
  "versionIdentifier": "v1.0",
  "effectiveDate": "2024-01-01",
  "authorityLevel": "OFFICIAL",
  "content": "Employees may work remotely up to 2 days per week with manager approval."
}
```

**Response (HTTP 201 Created):**
```json
{
  "id": "10201dfa-6900-4baa-8bbf-573931e307bf",
  "documentId": "8836a6a7-2e4b-4a40-8155-992d15899e8f",
  "documentTitle": "Remote Work and Data Security Policy",
  "versionIdentifier": "v1.0",
  "uploadTimestamp": "2026-10-20T14:23:35.430Z",
  "effectiveDate": "2024-01-01",
  "authorityLevel": "OFFICIAL",
  "authorityRank": 3,
  "contentHash": "a2fd416105d299f8536f5d635110e5a8990036248609eaceb6d4a5bfe8458c61",
  "processingStatus": "PENDING",
  "supersedesVersionId": null,
  "supersedesVersionIdentifier": null,
  "supersededById": null,
  "supersededByVersionIdentifier": null,
  "isSuperseded": false,
  "rawContent": "Employees may work remotely up to 2 days per week with manager approval."
}
```

### 3. Duplicate Version Upload Rejection
Uploading identical content returns HTTP 409 Conflict:
```json
{
  "timestamp": "2026-10-20T14:23:36.100Z",
  "status": 409,
  "error": "DuplicateContent",
  "message": "Document 8836a6a7-2e4b-4a40-8155-992d15899e8f already contains an identical version with content hash: a2fd416105d299f8536f5d635110e5a8990036248609eaceb6d4a5bfe8458c61",
  "path": "/api/v1/documents/8836a6a7-2e4b-4a40-8155-992d15899e8f/versions"
}
```

### 4. Explicit Supersession
**Request:**
```http
POST /api/v1/documents/8836a6a7-2e4b-4a40-8155-992d15899e8f/versions/10201dfa-6900-4baa-8bbf-573931e307bf/supersede HTTP/1.1
Content-Type: application/json

{
  "supersededByVersionId": "3ce495d3-bfa4-45c1-a55f-e335eaefb6b3"
}
```

**Response (HTTP 200 OK):**
```json
{
  "id": "10201dfa-6900-4baa-8bbf-573931e307bf",
  "versionIdentifier": "v1.0",
  "isSuperseded": true,
  "supersededById": "3ce495d3-bfa4-45c1-a55f-e335eaefb6b3",
  "supersededByVersionIdentifier": "v2.0"
}
```

---

## Running Locally

### Build and Test
```bash
cd backend
mvn clean test
```

### Start the Service
```bash
cd backend
mvn spring-boot:run
```
