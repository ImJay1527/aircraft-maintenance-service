#!/usr/bin/env bash
# Generates or imports the development PKI for aircraft-maintenance-service (TLS 1.3 / encryption in transit).
#
#   certs/ca.crt           dev CA certificate
#   certs/truststore.p12   truststore containing the CA certificate for outgoing calls
#   certs/aircraft.p12     private key and certificate for aircraft-maintenance-service
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT="$ROOT_DIR/certs"
SHARED_CERTS="$ROOT_DIR/../flight-operations-service/certs"
PASS="${SSL_PASSWORD:-changeit}"

mkdir -p "$OUT"

# 1. If certificates already exist in sibling flight-operations-service, copy them so both services share the exact same CA:
if [[ -f "$SHARED_CERTS/aircraft.p12" && -f "$SHARED_CERTS/truststore.p12" ]]; then
  echo "Importing shared certificates from $SHARED_CERTS..."
  cp "$SHARED_CERTS/ca.crt" "$OUT/ca.crt" 2>/dev/null || true
  cp "$SHARED_CERTS/truststore.p12" "$OUT/truststore.p12"
  cp "$SHARED_CERTS/aircraft.p12" "$OUT/aircraft.p12"
  echo "Certificates successfully imported into $OUT"
  exit 0
fi

# 2. Otherwise generate fresh standalone certificates
cd "$OUT"
rm -f ./*.p12 ./*.crt ./*.csr

echo "-> Generating AISafe Dev CA..."
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=AISafe Dev CA, O=ISEP SIDIS" -ext bc:c \
  -keystore ca.p12 -storetype PKCS12 -storepass "$PASS"
keytool -exportcert -alias ca -keystore ca.p12 -storepass "$PASS" -rfc -file ca.crt
keytool -importcert -noprompt -alias aisafe-ca -file ca.crt \
  -keystore truststore.p12 -storetype PKCS12 -storepass "$PASS"

echo "-> Generating aircraft certificate..."
keytool -genkeypair -alias aircraft -keyalg RSA -keysize 2048 -validity 825 \
  -dname "CN=aircraft, O=ISEP SIDIS" \
  -keystore aircraft.p12 -storetype PKCS12 -storepass "$PASS"
keytool -certreq -alias aircraft -keystore aircraft.p12 -storepass "$PASS" -file aircraft.csr
keytool -gencert -alias ca -keystore ca.p12 -storepass "$PASS" -rfc -validity 825 \
  -infile aircraft.csr -outfile aircraft.crt \
  -ext "SAN=dns:localhost,ip:127.0.0.1,dns:aircraft,dns:aircraft-1,dns:aircraft-2,dns:aircraft-3" \
  -ext "KU=digitalSignature,keyEncipherment" -ext "EKU=serverAuth,clientAuth"
keytool -importcert -noprompt -alias aisafe-ca -file ca.crt -keystore aircraft.p12 -storepass "$PASS"
keytool -importcert -noprompt -alias aircraft -file aircraft.crt -keystore aircraft.p12 -storepass "$PASS"
rm -f aircraft.csr

echo "Done. Certificates generated in $OUT"
