#!/usr/bin/env bash
# Starts N aircraft-maintenance instances locally. Ctrl+C stops all of them.
#
#   ./scripts/run-local.sh            2 instances, in-memory H2 (8081, 8091)
#   ./scripts/run-local.sh -n 3       3 instances (8081, 8091, 8101)
#   ./scripts/run-local.sh --postgres PostgreSQL instead of in-memory H2
#
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -d "/home/andrepm2004/.jdks/corretto-21.0.12.1" ]]; then
  export JAVA_HOME="/home/andrepm2004/.jdks/corretto-21.0.12.1"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

N=2
PROFILE=""
DB=h2
TLS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n) N="$2"; [[ "$N" =~ ^[123]$ ]] || { echo "-n must be 1, 2 or 3"; exit 1; }; shift 2 ;;
    --postgres) DB=postgres; shift ;;
    --tls) TLS=true; PROFILE="${PROFILE:+$PROFILE,}tls"; shift ;;
    *) echo "unknown option: $1"; exit 1 ;;
  esac
done

if [[ "$TLS" == true ]]; then
  if [[ ! -f "certs/aircraft.p12" ]]; then
    echo "Generating certificates for TLS..."
    ./scripts/generate-dev-certs.sh
  fi
fi

port() { echo $((8081 + 10 * ($1 - 1))); }

# Check for occupied ports
for i in $(seq 1 "$N"); do
  if (exec 3<>"/dev/tcp/127.0.0.1/$(port "$i")") 2>/dev/null; then
    echo "Port $(port "$i") is already in use - stop the running instance(s) first."
    exit 1
  fi
done

if [[ "$DB" == postgres ]]; then
  PROFILE="${PROFILE:+$PROFILE,}postgres"
fi

echo "Building aircraft-maintenance-service..."
./mvnw -q -B package -DskipTests
JAR=$(ls target/aircraft-maintenance-service-*.jar | head -1)
mkdir -p logs

PIDS=()
stop_port() {
  if command -v lsof >/dev/null; then
    kill $(lsof -t -i:"$1" -sTCP:LISTEN) 2>/dev/null || true
  elif command -v fuser >/dev/null; then
    fuser -k "$1/tcp" 2>/dev/null || true
  fi
}

cleanup() {
  trap '' INT TERM; trap - EXIT
  echo; echo "Stopping aircraft-maintenance instances..."
  for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
  for i in $(seq 1 "$N"); do stop_port "$(port "$i")"; done
  wait 2>/dev/null || true
  echo "All instances stopped."
}
trap cleanup INT TERM EXIT

for i in $(seq 1 "$N"); do
  echo "Starting aircraft-$i on port $(port "$i")..."
  java -jar "$JAR" --spring.profiles.active="instance$i${PROFILE:+,$PROFILE}" > "logs/aircraft-$i.log" 2>&1 &
  PIDS+=($!)
done

./scripts/wait-ready.sh -n "$N" $([[ "$TLS" == true ]] && echo "--tls")

echo "Instances running. Press Ctrl+C to stop."
wait
