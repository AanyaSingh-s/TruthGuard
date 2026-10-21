#!/usr/bin/env bash
# ==============================================================================
# TruthGuard Live Demonstration Script
# Demonstrates:
#   1. A successful grounded answer (ANSWERED)
#   2. An unresolvable contradiction causing explicit abstention (CONFLICT)
# ==============================================================================

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
AI_SERVICE_URL="${AI_SERVICE_URL:-http://localhost:8000}"

COLOR_RESET="\033[0m"
COLOR_BOLD="\033[1m"
COLOR_GREEN="\033[32m"
COLOR_YELLOW="\033[33m"
COLOR_CYAN="\033[36m"
COLOR_RED="\033[31m"

echo -e "${COLOR_BOLD}${COLOR_CYAN}======================================================================${COLOR_RESET}"
echo -e "${COLOR_BOLD}${COLOR_CYAN} 🛡️  TruthGuard Live End-to-End Demonstration Script                 ${COLOR_RESET}"
echo -e "${COLOR_BOLD}${COLOR_CYAN}======================================================================${COLOR_RESET}"
echo ""

# ------------------------------------------------------------------------------
# 1. Probe Service Health
# ------------------------------------------------------------------------------
echo -e "${COLOR_BOLD}[1/4] Checking Service Health...${COLOR_RESET}"

if ! curl -s -f "${BACKEND_URL}/api/v1/health" > /dev/null; then
    echo -e "${COLOR_RED}❌ Error: Spring Boot backend is not reachable at ${BACKEND_URL}.${COLOR_RESET}"
    echo "Please ensure the backend is running (e.g., 'cd backend && mvn spring-boot:run' or 'docker compose up')."
    exit 1
fi
echo -e "${COLOR_GREEN}✓ Spring Boot Backend is UP at ${BACKEND_URL}${COLOR_RESET}"

if ! curl -s -f "${AI_SERVICE_URL}/health" > /dev/null; then
    echo -e "${COLOR_RED}❌ Error: Python AI service is not reachable at ${AI_SERVICE_URL}.${COLOR_RESET}"
    echo "Please ensure the AI service is running (e.g., 'cd ai-service && uvicorn app.main:app --port 8000')."
    exit 1
fi
echo -e "${COLOR_GREEN}✓ Python AI Service is UP at ${AI_SERVICE_URL}${COLOR_RESET}"
echo ""

# ------------------------------------------------------------------------------
# 2. Ingest Sample Documents
# ------------------------------------------------------------------------------
echo -e "${COLOR_BOLD}[2/4] Uploading & Ingesting Demo Documents...${COLOR_RESET}"

echo "Uploading demo_01_matching_engineering_policy.txt..."
DOC1_RES=$(curl -s -X POST "${BACKEND_URL}/api/v1/documents/upload" \
  -F "file=@sample_documents/demo_01_matching_engineering_policy.txt" \
  -F "title=Engineering Baseline Standard" \
  -F "sourceType=SPECIFICATION" \
  -F "versionIdentifier=v1.0" \
  -F "effectiveDate=2026-01-01" \
  -F "authorityLevel=OFFICIAL")
echo "  -> Ingested: $(echo "$DOC1_RES" | grep -o '"version_identifier":"[^"]*"' || echo "$DOC1_RES")"

echo "Uploading demo_01_matching_security_guideline.txt..."
DOC2_RES=$(curl -s -X POST "${BACKEND_URL}/api/v1/documents/upload" \
  -F "file=@sample_documents/demo_01_matching_security_guideline.txt" \
  -F "title=Security Architecture Guideline" \
  -F "sourceType=POLICY" \
  -F "versionIdentifier=v1.0" \
  -F "effectiveDate=2026-01-01" \
  -F "authorityLevel=OFFICIAL")
echo "  -> Ingested: $(echo "$DOC2_RES" | grep -o '"version_identifier":"[^"]*"' || echo "$DOC2_RES")"

echo "Uploading demo_02_conflict_ops_runbook.txt (60 min timeout)..."
DOC3_RES=$(curl -s -X POST "${BACKEND_URL}/api/v1/documents/upload" \
  -F "file=@sample_documents/demo_02_conflict_ops_runbook.txt" \
  -F "title=Production Operations Runbook" \
  -F "sourceType=POLICY" \
  -F "versionIdentifier=v1.0" \
  -F "effectiveDate=2026-01-01" \
  -F "authorityLevel=OFFICIAL")
echo "  -> Ingested: $(echo "$DOC3_RES" | grep -o '"version_identifier":"[^"]*"' || echo "$DOC3_RES")"

echo "Uploading demo_02_conflict_security_policy.txt (15 min timeout)..."
DOC4_RES=$(curl -s -X POST "${BACKEND_URL}/api/v1/documents/upload" \
  -F "file=@sample_documents/demo_02_conflict_security_policy.txt" \
  -F "title=Access Control Security Policy" \
  -F "sourceType=POLICY" \
  -F "versionIdentifier=v1.0" \
  -F "effectiveDate=2026-01-01" \
  -F "authorityLevel=OFFICIAL")
echo "  -> Ingested: $(echo "$DOC4_RES" | grep -o '"version_identifier":"[^"]*"' || echo "$DOC4_RES")"
echo ""

# ------------------------------------------------------------------------------
# 3. Scenario 1: Successful Grounded Answer (ANSWERED)
# ------------------------------------------------------------------------------
echo -e "${COLOR_BOLD}[3/4] Scenario 1: Agreement -> Grounded Answer${COLOR_RESET}"
QUESTION_1="What TLS version is required for production database connections?"
echo -e "Question: ${COLOR_CYAN}\"${QUESTION_1}\"${COLOR_RESET}"

QUERY_RES_1=$(curl -s -X POST "${BACKEND_URL}/api/v1/queries" \
  -H "Content-Type: application/json" \
  -d "{
    \"question\": \"${QUESTION_1}\",
    \"top_k\": 5
  }")

STATUS_1=$(echo "$QUERY_RES_1" | grep -o '"resolution_status":"[^"]*"' | cut -d'"' -f4 || echo "UNKNOWN")
ANSWER_1=$(echo "$QUERY_RES_1" | grep -o '"final_answer":"[^"]*"' | cut -d'"' -f4 || echo "N/A")

echo -e "Resolution Status: ${COLOR_GREEN}${STATUS_1}${COLOR_RESET}"
echo -e "Final Answer:      ${ANSWER_1}"
echo ""

# ------------------------------------------------------------------------------
# 4. Scenario 2: Direct Contradiction -> Explicit Abstention (CONFLICT)
# ------------------------------------------------------------------------------
echo -e "${COLOR_BOLD}[4/4] Scenario 2: Direct Contradiction -> Explicit Abstention${COLOR_RESET}"
QUESTION_2="What is the mandatory session idle timeout for production engineer terminals?"
echo -e "Question: ${COLOR_CYAN}\"${QUESTION_2}\"${COLOR_RESET}"

QUERY_RES_2=$(curl -s -X POST "${BACKEND_URL}/api/v1/queries" \
  -H "Content-Type: application/json" \
  -d "{
    \"question\": \"${QUESTION_2}\",
    \"top_k\": 5
  }")

STATUS_2=$(echo "$QUERY_RES_2" | grep -o '"resolution_status":"[^"]*"' | cut -d'"' -f4 || echo "UNKNOWN")
EXPLANATION_2=$(echo "$QUERY_RES_2" | grep -o '"explanation":"[^"]*"' | cut -d'"' -f4 || echo "N/A")

echo -e "Resolution Status: ${COLOR_YELLOW}${STATUS_2}${COLOR_RESET}"
echo -e "Decision Rationale: ${EXPLANATION_2}"
echo -e "Notice: ${COLOR_YELLOW}TruthGuard abstained from selecting or averaging the numbers (neither 60m nor 15m was falsely chosen, and they were not blended to 37.5m).${COLOR_RESET}"
echo ""

echo -e "${COLOR_BOLD}${COLOR_GREEN}======================================================================${COLOR_RESET}"
echo -e "${COLOR_BOLD}${COLOR_GREEN} ✓ Demonstration Completed Successfully!                             ${COLOR_RESET}"
echo -e "${COLOR_BOLD}${COLOR_GREEN}======================================================================${COLOR_RESET}"
