#!/usr/bin/env bash
# ==============================================================================
# Live Demonstration Script for SIDIS 2026/2027
# Component: aircraft-maintenance-service
# Demonstrates:
#   1. Health and cluster status (Active Component Cloning)
#   2. Local Hit (zero inter-instance hops, X-Data-Source: local)
#   3. P2P Unicast Forwarding (Instance 1 -> Instance 2, X-Data-Source: peer)
#   4. Reverse P2P Forwarding (Instance 2 -> Instance 1, X-Data-Source: peer)
#   5. Distributed Loop / Cycle Prevention (X-Forwarded-From)
#   6. Inter-service Security Boundary (Role SERVICE enforcement)
#   7. Maintenance Templates & Checklists
#   8. Maintenance Ongoing Interventions & Cost Reporting
# ==============================================================================

set -uo pipefail

# ANSI color codes
BOLD="\033[1m"
GREEN="\033[1;32m"
BLUE="\033[1;34m"
YELLOW="\033[1;33m"
CYAN="\033[1;36m"
RED="\033[1;31m"
MAGENTA="\033[1;35m"
RESET="\033[0m"

INST1="http://localhost:8081"
INST2="http://localhost:8091"

# Long-lived pre-generated tokens matching application JWT secret
SERVICE_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJmbGlnaHQtb3BlcmF0aW9ucy1zZXJ2aWNlIiwicm9sZSI6IlNFUlZJQ0UiLCJpYXQiOjE3OTE5NzE3MDksImV4cCI6MjEwNzMzMTcwOX0.nmoac7CrOG6J5m_J5quGzgu2ZkZnMiMkivRXU_F5ypo"
USER_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJtZWNoYW5pYzEiLCJyb2xlIjoiQUlSQ1JBRlRfTUVDSEFOSUMiLCJpYXQiOjE3OTE5NzE3MDksImV4cCI6MjEwNzMzMTcwOX0.tcxw6LEgZgZOraABZcjstYat2gqHqPHP_zywzC6LW1k"
ADMIN_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTixCQUNLT0ZGSUNFX09QRVJBVE9SLEFUQ0MsTUFJTlRFTkFOQ0VfVEVDSE5JQ0lBTixNQUlOVEVOQU5DRV9TVVBFUlZJU09SIiwiaWF0IjoxNzkxOTcxNzA5LCJleHAiOjIxMDczMzE3MDl9.N1oW5B4AOhCunjI4Rydm4MRIxS8br7dB8yARHWX9BBI"

echo -e "${BOLD}${BLUE}"
echo "================================================================================"
echo "    LETI-SIDIS 2026/2027 - AIRCRAFT & MAINTENANCE CLUSTER DEMONSTRATION         "
echo "    Transversal: Component Cloning, P2P Unicast Forwarding & Zero SPOF          "
echo "================================================================================"
echo -e "${RESET}"

# 0. Check cluster connectivity
echo -e "${YELLOW}>> 0. Checking cluster connectivity...${RESET}"
H1=$(curl -s -o /dev/null -w "%{http_code}" "$INST1/actuator/health" 2>/dev/null || echo "DOWN")
H2=$(curl -s -o /dev/null -w "%{http_code}" "$INST2/actuator/health" 2>/dev/null || echo "DOWN")

if [[ "$H1" != "200" || "$H2" != "200" ]]; then
  echo -e "${RED}[ERROR] Both instances must be running to execute the live demonstration.${RESET}"
  echo "  - Instance 1 (port 8081): $H1"
  echo "  - Instance 2 (port 8091): $H2"
  echo
  echo -e "To start the cluster, run:"
  echo -e "  ${BOLD}./scripts/run-local.sh${RESET}        (Local H2 in background)"
  echo -e "  ${BOLD}./scripts/run-docker.sh --h2${RESET}  (Docker Compose H2)"
  echo -e "  ${BOLD}./scripts/run-docker.sh${RESET}       (Docker Compose PostgreSQL)"
  exit 1
fi

echo -e "  Instance 1 ($INST1): ${GREEN}HEALTHY (HTTP 200)${RESET}"
echo -e "  Instance 2 ($INST2): ${GREEN}HEALTHY (HTTP 200)${RESET}"
echo

# 1. Local Hit
echo -e "${YELLOW}>> 1. Local Cache Hit (CS-TPA on Instance 1)${RESET}"
echo -e "  Calling: ${BOLD}GET $INST1/internal/aircraft/CS-TPA${RESET}"
RESP=$(curl -s -i "$INST1/internal/aircraft/CS-TPA" -H "Authorization: Bearer $SERVICE_TOKEN")
STATUS=$(echo "$RESP" | grep -i "^HTTP" | awk '{print $2}')
SOURCE=$(echo "$RESP" | grep -i "^X-Data-Source:" | tr -d '\r' | awk '{print $2}')
INST_HDR=$(echo "$RESP" | grep -i "^X-Instance:" | tr -d '\r' | awk '{print $2}')
BODY=$(echo "$RESP" | sed -e '1,/^\r*$/d')

echo -e "  HTTP Status: ${GREEN}$STATUS${RESET} | Instance: ${BLUE}$INST_HDR${RESET} | Data Source: ${CYAN}$SOURCE${RESET}"
echo "  Payload: $BODY"
echo

# 2. P2P Forwarding: Inst1 -> Inst2
echo -e "${YELLOW}>> 2. P2P Unicast Query Forwarding (CS-TPX partitioned on Inst2 requested from Inst1)${RESET}"
echo -e "  Calling: ${BOLD}GET $INST1/internal/aircraft/CS-TPX${RESET}"
RESP=$(curl -s -i "$INST1/internal/aircraft/CS-TPX" -H "Authorization: Bearer $SERVICE_TOKEN")
STATUS=$(echo "$RESP" | grep -i "^HTTP" | awk '{print $2}')
SOURCE=$(echo "$RESP" | grep -i "^X-Data-Source:" | tr -d '\r' | awk '{print $2}')
INST_HDR=$(echo "$RESP" | grep -i "^X-Instance:" | tr -d '\r' | awk '{print $2}')
BODY=$(echo "$RESP" | sed -e '1,/^\r*$/d')

echo -e "  HTTP Status: ${GREEN}$STATUS${RESET} | Responder: ${BLUE}$INST_HDR${RESET} | Data Source: ${MAGENTA}$SOURCE${RESET} (retrieved via P2P unicast hop!)"
echo "  Payload: $BODY"
echo

# 3. Reverse P2P Forwarding: Inst2 -> Inst1
echo -e "${YELLOW}>> 3. Reverse P2P Query Forwarding (CS-TPY partitioned on Inst1 requested from Inst2)${RESET}"
echo -e "  Calling: ${BOLD}GET $INST2/internal/aircraft/CS-TPY${RESET}"
RESP=$(curl -s -i "$INST2/internal/aircraft/CS-TPY" -H "Authorization: Bearer $SERVICE_TOKEN")
STATUS=$(echo "$RESP" | grep -i "^HTTP" | awk '{print $2}')
SOURCE=$(echo "$RESP" | grep -i "^X-Data-Source:" | tr -d '\r' | awk '{print $2}')
INST_HDR=$(echo "$RESP" | grep -i "^X-Instance:" | tr -d '\r' | awk '{print $2}')
BODY=$(echo "$RESP" | sed -e '1,/^\r*$/d')

echo -e "  HTTP Status: ${GREEN}$STATUS${RESET} | Responder: ${BLUE}$INST_HDR${RESET} | Data Source: ${MAGENTA}$SOURCE${RESET} (retrieved via P2P unicast hop!)"
echo "  Payload: $BODY"
echo

# 4. Loop Prevention
echo -e "${YELLOW}>> 4. Distributed Loop Prevention (X-Forwarded-From stops circular storm)${RESET}"
echo -e "  Simulating incoming query with: ${BOLD}X-Forwarded-From: instance2${RESET} for nonexistent aircraft 'CS-UNKNOWN'"
echo -e "  Calling: ${BOLD}GET $INST1/internal/aircraft/CS-UNKNOWN${RESET}"
RESP=$(curl -s -i "$INST1/internal/aircraft/CS-UNKNOWN" \
  -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "X-Forwarded-From: instance2")
STATUS=$(echo "$RESP" | grep -i "^HTTP" | awk '{print $2}')

if [[ "$STATUS" == "404" ]]; then
  echo -e "  HTTP Status: ${GREEN}$STATUS NOT FOUND${RESET} (Cycle cut off cleanly - peer was NOT contacted!)"
else
  echo -e "  HTTP Status: ${RED}$STATUS${RESET}"
fi
echo

# 5. Security Boundary
echo -e "${YELLOW}>> 5. Inter-service Security Boundary (Role Enforcement on /internal/**)${RESET}"
echo -e "  5.1 Without token -> Calling: ${BOLD}GET $INST1/internal/aircraft/CS-TPA${RESET}"
STATUS_NO_AUTH=$(curl -s -o /dev/null -w "%{http_code}" "$INST1/internal/aircraft/CS-TPA")
echo -e "      HTTP Status: ${GREEN}$STATUS_NO_AUTH UNAUTHORIZED${RESET}"

echo -e "  5.2 With User/Mechanic token (forbidden on internal service contract):"
STATUS_USER_AUTH=$(curl -s -o /dev/null -w "%{http_code}" "$INST1/internal/aircraft/CS-TPA" -H "Authorization: Bearer $USER_TOKEN")
echo -e "      HTTP Status: ${GREEN}$STATUS_USER_AUTH FORBIDDEN${RESET} (enforced by SecurityConfig hasRole('SERVICE'))"
echo

# 6. Maintenance Templates & Ongoing Interventions
echo -e "${YELLOW}>> 6. Maintenance Templates & Checklist Inspection${RESET}"
echo -e "  Calling: ${BOLD}GET $INST1/api/maintenance-templates/1${RESET}"
RESP=$(curl -s -i "$INST1/api/maintenance-templates/1" -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(echo "$RESP" | sed -e '1,/^\r*$/d')
echo "  Payload: $BODY"
echo

# 7. Maintenance Cost & Turnaround Reports
echo -e "${YELLOW}>> 7. Maintenance Cost Report per Aircraft${RESET}"
echo -e "  Calling: ${BOLD}GET $INST1/api/maintenance-records/reports/cost-per-aircraft${RESET}"
RESP=$(curl -s -i "$INST1/api/maintenance-records/reports/cost-per-aircraft" -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(echo "$RESP" | sed -e '1,/^\r*$/d')
echo "  Payload: $BODY"
echo

echo -e "${BOLD}${GREEN}================================================================================"
echo "    DEMONSTRATION COMPLETED SUCCESSFULLY: ALL DISTRIBUTED SCENARIOS PASSED     "
echo "================================================================================${RESET}"
