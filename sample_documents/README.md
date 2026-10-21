# 📁 TruthGuard Reproducible Demonstration Corpus

This directory contains standardized, synthetic document files designed to reproduce and demonstrate TruthGuard's core verification and resolution capabilities.

---

## 🎯 Demonstration Scenarios

| Category | File(s) | Question | Expected Status | Description & Rationale |
|---|---|---|---|---|
| **1. Matching / Agreement** | `demo_01_matching_engineering_policy.txt`<br>`demo_01_matching_security_guideline.txt` | *"What TLS version is required for production database connections?"* | **`ANSWERED`** | Both sources independently confirm **TLS 1.3**. Since the evidence agrees, TruthGuard generates a fully grounded answer with both citations. |
| **2. Contradiction / Abstention** | `demo_02_conflict_ops_runbook.txt`<br>`demo_02_conflict_security_policy.txt` | *"What is the mandatory session idle timeout for production engineer terminals?"* | **`CONFLICT`** | The Operations Runbook specifies **60 minutes** while the Security Policy specifies **15 minutes**. Both carry equal authority (`OFFICIAL`) and matching effective dates (2026-01-01). TruthGuard explicitly abstains and preserves both contradictory positions without compromising or averaging to 37.5 minutes. |
| **3. Superseded / Resolvable Conflict** | `demo_03_superseded_retention_v1.txt`<br>`demo_03_superseding_retention_v2.txt` | *"What is the mandatory customer audit log retention duration?"* | **`ANSWERED_WITH_QUALIFICATION`** | v1 specifies **365 days** (OFFICIAL, 2024), but v2 specifies **730 days** (EXECUTIVE, 2025) and explicitly revokes v1. TruthGuard upholds the 730-day requirement while documenting that v1 has been superseded. |
| **4. Insufficient Evidence** | `demo_04_insufficient_facility_manual.txt` | *"What is the corporate reimbursement rate per mile for employee travel?"* | **`INSUFFICIENT_EVIDENCE`** | The corpus covers building hours and cafeteria times, but contains zero information on travel mileage reimbursement. TruthGuard detects the absence of relevant evidence and refuses to fabricate an answer. |

---

## 🚀 How to Run the Demonstration

### Option A: Using the React Verification Dashboard (UI)
1. Open the dashboard at `http://localhost:3000`.
2. In the **Document Upload** card:
   - Select and upload the relevant demo file.
   - Set the title, version, effective date, and authority level matching the header in the file.
   - If uploading `demo_03_superseding_retention_v2.txt`, select the v1 document in the *Supersedes Version* dropdown.
3. In the **Question Panel**:
   - Enter the query or click one of the quick scenario chips (`TLS Requirements`, `Retention Period`, `Session Timeout Conflict`, `Cafeteria Hours`).
   - Click **Run Pipeline Analysis**.
4. Observe:
   - **Resolution Status Badge**: `ANSWERED`, `CONFLICT`, or `ANSWERED_WITH_QUALIFICATION`.
   - **Answer Display**: The verified answer or an explicit abstention warning banner.
   - **Evidence Panel**: Grounded chunk passages and metadata.
   - **Conflict Comparison**: Side-by-side comparison of contradictory statements when conflict occurs.
   - **Expandable Agent Trace**: Stage timing for retrieval, synthesis, criticism, and resolution.

### Option B: Using `curl` REST API

#### 1. Upload Document 1 (Ops Runbook - 60 min timeout)
```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@sample_documents/demo_02_conflict_ops_runbook.txt" \
  -F "title=Production Operations Runbook" \
  -F "sourceType=POLICY" \
  -F "versionIdentifier=v1.0" \
  -F "effectiveDate=2026-01-01" \
  -F "authorityLevel=OFFICIAL"
```

#### 2. Upload Document 2 (Security Policy - 15 min timeout)
```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@sample_documents/demo_02_conflict_security_policy.txt" \
  -F "title=Access Control Security Policy" \
  -F "sourceType=POLICY" \
  -F "versionIdentifier=v1.0" \
  -F "effectiveDate=2026-01-01" \
  -F "authorityLevel=OFFICIAL"
```

#### 3. Ask Question (Query Endpoint)
```bash
curl -X POST http://localhost:8080/api/v1/queries \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the mandatory session idle timeout for production engineer terminals?",
    "top_k": 5
  }'
```

**Expected JSON Response Status:** `"resolution_status": "CONFLICT"` with `conflicting_positions` detailing both the 60-minute and 15-minute clauses.
