#!/usr/bin/env bash
# DAST (G5): run OWASP ZAP's API scan against the built jar + a throwaway Postgres.
# Identical locally and in CI:  tools/security/dast.sh <path/to/backend.jar> <output-dir>
#
# - Active scanning, so ONLY ever against this ephemeral environment, never a shared/prod one.
# - The scan is driven by our OpenAPI spec; its example values are valid on purpose.
# - ZAP attaches a valid CSRF cookie + header to every request (replacer rules). Without that,
#   every attack on a state-changing endpoint stops at a 403 and tests nothing.
# - Coverage check: the scan must have created tenants, i.e. it got past CSRF and validation
#   all the way to the database. A scan that only ever saw 403/400 is a false "all clear".
set -euo pipefail

JAR=${1:?usage: dast.sh <jar> <output-dir>}
OUT=${2:?usage: dast.sh <jar> <output-dir>}
ZAP_IMAGE=${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef} # 2.17.0
PG_IMAGE=${PG_IMAGE:-postgres@sha256:1a6ab3f5345eb6dbe04a1349529caabdb0ab09293a09590fad07b2246bfa4b54} # 16.15
DB_PORT=${DB_PORT:-55432}   # not 5432/8080, so it never collides with a running dev stack
APP_PORT=${APP_PORT:-18080}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DB_NAME=dast-db-$$
APP_PID=

cleanup() {
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null || true
  docker rm -f "$DB_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mkdir -p "$OUT" && chmod 777 "$OUT" # ZAP's container runs as its own uid and writes reports here
DB_PASSWORD=$(openssl rand -hex 16)
DB_APP_PASSWORD=$(openssl rand -hex 16)
if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::add-mask::$DB_PASSWORD"; echo "::add-mask::$DB_APP_PASSWORD"; fi

echo "--- ephemeral Postgres (same least-privilege init as local dev)"
docker run -d --name "$DB_NAME" -p "127.0.0.1:$DB_PORT:5432" \
  -e POSTGRES_DB=sevacenter -e POSTGRES_USER=sevacenter -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e DB_APP_PASSWORD="$DB_APP_PASSWORD" -v "$ROOT/docker/db-init:/docker-entrypoint-initdb.d:ro" \
  "$PG_IMAGE" >/dev/null
for _ in $(seq 60); do
  # pg_isready over TCP only succeeds once init scripts finished and the real server is up
  docker exec "$DB_NAME" pg_isready -h 127.0.0.1 -U sevacenter -d sevacenter >/dev/null 2>&1 && break
  sleep 1
done

echo "--- app under test: $JAR"
JDBC_URL="jdbc:postgresql://127.0.0.1:$DB_PORT/sevacenter" # trufflehog:ignore — no credentials in URL
SPRING_PROFILES_ACTIVE=local SERVER_PORT=$APP_PORT \
  DB_URL="$JDBC_URL" DB_USERNAME=sevacenter \
  DB_PASSWORD="$DB_PASSWORD" DB_APP_USERNAME=sevacenter_app DB_APP_PASSWORD="$DB_APP_PASSWORD" \
  java -jar "$JAR" > "$OUT/app.log" 2>&1 &
APP_PID=$!
for _ in $(seq 90); do
  curl -sf "http://127.0.0.1:$APP_PORT/actuator/health" >/dev/null && break
  kill -0 "$APP_PID" 2>/dev/null || { echo "::error::app exited during startup"; tail -30 "$OUT/app.log"; exit 1; }
  sleep 1
done
curl -sf "http://127.0.0.1:$APP_PORT/actuator/health" >/dev/null || { echo "::error::app never became healthy"; exit 1; }

echo "--- ZAP API scan (active, full Default Policy via .zap/rules.tsv)"
cp "$ROOT/.zap/rules.tsv" "$OUT/rules.tsv" # -c resolves inside ZAP's /zap/wrk mount
TOKEN=$(curl -sf "http://127.0.0.1:$APP_PORT/api/v1/csrf" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
replace() { # index, description, header, value
  printf -- '-config replacer.full_list(%s).%s ' \
    "$1" "description=$2" "$1" enabled=true "$1" matchtype=REQ_HEADER "$1" "matchstr=$3" \
    "$1" regex=false "$1" "replacement=$4"
}
ZAP_OPTS="$(replace 0 csrf-header X-XSRF-TOKEN "$TOKEN")$(replace 1 csrf-cookie Cookie "XSRF-TOKEN=$TOKEN")"
rc=0
docker run --rm --network host -v "$(cd "$OUT" && pwd):/zap/wrk:rw" "$ZAP_IMAGE" \
  zap-api-scan.py -t "http://127.0.0.1:$APP_PORT/v3/api-docs" -f openapi \
  -c rules.tsv -J zap.json -r zap.html -I -z "$ZAP_OPTS" || rc=$?
# -I: warnings don't fail here; the policy gate (dast_policy.py) decides. 0/1 = scan ran; 3 = error.
if [ "$rc" -gt 1 ]; then echo "::error::ZAP scan failed (exit $rc)"; exit "$rc"; fi
[ -s "$OUT/zap.json" ] || { echo "::error::ZAP produced no report"; exit 1; }

echo "--- coverage: did the attacks reach the database?"
TENANTS=$(docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_NAME" \
  psql -h 127.0.0.1 -U sevacenter -d sevacenter -tAc 'select count(*) from tenant')
SERVER_ERRORS=$(grep -c ' ERROR ' "$OUT/app.log" || true)
echo "tenants created by the scan: $TENANTS | app ERROR log lines: $SERVER_ERRORS"
if [ "$TENANTS" -lt 1 ]; then
  echo "::error::DAST never got past CSRF/validation (0 tenants created): the scan tested nothing"
  exit 1
fi
