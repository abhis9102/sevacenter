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
# - M1 slice 2c, three stages against the same jar:
#   1. authz_probe.py: cross-tenant + role matrix (ZAP can't judge access control). First,
#      because the per-IP login throttle allows few failures and stage 3 attacks the login.
#   2. ZAP as a logged-in TRUST_ADMIN (session cookie + tenant header), spec minus
#      login/logout (replaying those would rotate or kill the session mid-scan) and register. Must still
#      be logged in afterwards, and must have created users: else it tested nothing.
#   3. ZAP unauthenticated (the G5 scan), including login/logout.
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

APP="http://127.0.0.1:$APP_PORT"

echo "--- 1/3 authz probe: cross-tenant + role matrix"
python3 "$ROOT/tools/security/authz_probe.py" "$APP" --out "$OUT/authz-probe.json"

cp "$ROOT/.zap/rules.tsv" "$OUT/rules.tsv" # -c resolves inside ZAP's /zap/wrk mount
replace() { # index, description, header, value
  printf -- '-config replacer.full_list(%s).%s ' \
    "$1" "description=$2" "$1" enabled=true "$1" matchtype=REQ_HEADER "$1" "matchstr=$3" \
    "$1" regex=false "$1" "replacement=$4"
}
zap() { # spec, report-name, replacer options
  local rc=0
  docker run --rm --network host -v "$(cd "$OUT" && pwd):/zap/wrk:rw" "$ZAP_IMAGE" \
    zap-api-scan.py -t "$1" -f openapi -c rules.tsv -J "$2.json" -r "$2.html" -I -z "$3" || rc=$?
  # -I: warnings don't fail here; the policy gate (dast_policy.py) decides. 0/1 = scan ran; 3 = error.
  if [ "$rc" -gt 1 ]; then echo "::error::ZAP scan $2 failed (exit $rc)"; exit "$rc"; fi
  [ -s "$OUT/$2.json" ] || { echo "::error::ZAP produced no report for $2"; exit 1; }
}
json_field() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)"; }

echo "--- 2/3 ZAP as a logged-in TRUST_ADMIN"
SLUG=dast-$(openssl rand -hex 4)
JAR_COOKIES=$(mktemp)
ADMIN_PASSWORD=$(openssl rand -hex 16)
TOKEN=$(curl -sf -c "$JAR_COOKIES" "$APP/api/v1/csrf" | json_field '["token"]')
curl -sf -b "$JAR_COOKIES" -X POST "$APP/api/v1/register" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $TOKEN" -o /dev/null -d "{\"slug\":\"$SLUG\",\"trustName\":\"DAST\",
  \"adminEmail\":\"admin@$SLUG.example\",\"adminPassword\":\"$ADMIN_PASSWORD\",\"adminName\":\"DAST\"}"
curl -sf -b "$JAR_COOKIES" -c "$JAR_COOKIES" -X POST "$APP/api/v1/auth/login" -H "X-Tenant-Slug: $SLUG" \
  -H "X-XSRF-TOKEN: $TOKEN" -o /dev/null --data-urlencode "email=admin@$SLUG.example" \
  --data-urlencode "password=$ADMIN_PASSWORD"
# Login rotates the CSRF token; fetch the new one with the session.
TOKEN=$(curl -sf -b "$JAR_COOKIES" -c "$JAR_COOKIES" -H "X-Tenant-Slug: $SLUG" "$APP/api/v1/csrf" | json_field '["token"]')
SESSION=$(awk '$6 == "SC_SESSION" {print $7}' "$JAR_COOKIES")
rm -f "$JAR_COOKIES"
[ -n "$SESSION" ] || { echo "::error::could not log in for the authenticated scan"; exit 1; }
if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::add-mask::$SESSION"; echo "::add-mask::$ADMIN_PASSWORD"; fi
me() { curl -s -o /dev/null -w '%{http_code}' -H "X-Tenant-Slug: $SLUG" -H "Cookie: SC_SESSION=$SESSION" "$APP/api/v1/me"; }
[ "$(me)" = 200 ] || { echo "::error::session not valid before the authenticated scan"; exit 1; }

curl -sf "$APP/v3/api-docs" | python3 -c '
import sys, json
spec = json.load(sys.stdin)
# login/logout would rotate or kill our session. register is public: the unauthenticated
# pass owns it (attacking it here too made that pass state-dependent: a false "path
# traversal" when its baseline slug was already taken).
for path in ("/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/register"):
    spec["paths"].pop(path, None)
json.dump(spec, sys.stdout)' > "$OUT/openapi-authed.json"
# No spaces in replacer values: zap-api-scan.py splits -z on whitespace. "a=1; b=2" silently
# lost the CSRF cookie, every request got 403, and ZAP still reported a clean scan (caught by
# the users-created check below).
# The tenant goes in the dev-only header: ZAP can't rewrite Host, and the app's Host allowlist
# rejects 127.0.0.1. authz_probe.py covers the real Host-based resolution.
zap /zap/wrk/openapi-authed.json zap-authed "$(replace 0 csrf-header X-XSRF-TOKEN "$TOKEN")$(replace 1 session-cookies Cookie "SC_SESSION=$SESSION;XSRF-TOKEN=$TOKEN")$(replace 2 tenant X-Tenant-Slug "$SLUG")"

echo "--- coverage: did the authenticated scan stay logged in and reach the database?"
SESSION_AFTER=$(me)
USERS=$(docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_NAME" psql -h 127.0.0.1 -U sevacenter -d sevacenter -tAc \
  "select count(*) from app_user u join tenant t on t.id = u.tenant_id where t.slug = '$SLUG' and u.email <> 'admin@$SLUG.example'")
DEVOTEES=$(docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_NAME" psql -h 127.0.0.1 -U sevacenter -d sevacenter -tAc \
  "select count(*) from devotee d join tenant t on t.id = d.tenant_id where t.slug = '$SLUG'")
DONATIONS=$(docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_NAME" psql -h 127.0.0.1 -U sevacenter -d sevacenter -tAc \
  "select count(*) from donation d join tenant t on t.id = d.tenant_id where t.slug = '$SLUG'")
echo "session after scan: HTTP $SESSION_AFTER | created by the scan: $USERS users, $DEVOTEES devotees, $DONATIONS donations"
if [ "$SESSION_AFTER" != 200 ]; then
  echo "::error::the session died during the authenticated scan, so later requests were tested as anonymous"
  exit 1
fi
# One row per feature: an endpoint the scan can't get a valid request through is untested.
for created in "users:$USERS" "devotees:$DEVOTEES" "donations:$DONATIONS"; do
  if [ "${created#*:}" -lt 1 ]; then
    echo "::error::the authenticated scan created no ${created%%:*}: it never got past authz/CSRF/validation"
    exit 1
  fi
done

tenants() { docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_NAME" \
  psql -h 127.0.0.1 -U sevacenter -d sevacenter -tAc 'select count(*) from tenant'; }
TENANTS_BEFORE=$(tenants) # stages 1-2 created some; only the ones stage 3 creates count

echo "--- 3/3 ZAP unauthenticated (active, full Default Policy via .zap/rules.tsv)"
TOKEN=$(curl -sf "$APP/api/v1/csrf" | json_field '["token"]')
zap "$APP/v3/api-docs" zap "$(replace 0 csrf-header X-XSRF-TOKEN "$TOKEN")$(replace 1 csrf-cookie Cookie "XSRF-TOKEN=$TOKEN")"

echo "--- coverage: did the attacks reach the database?"
TENANTS=$(( $(tenants) - TENANTS_BEFORE ))
SERVER_ERRORS=$(grep -c ' ERROR ' "$OUT/app.log" || true)
echo "tenants created by the unauthenticated scan: $TENANTS | app ERROR log lines: $SERVER_ERRORS"
if [ "$TENANTS" -lt 1 ]; then
  echo "::error::DAST never got past CSRF/validation (0 tenants created): the scan tested nothing"
  exit 1
fi
