#!/usr/bin/env bash
# IaC scan (G7): the Terraform under infra/, with Checkov + our custom checks (.checkov/checks).
# Identical locally and in CI:  tools/security/iac_scan.sh <output-dir>
# Policy: docs/security/iac-scan.md
#
# 1. Accepted risks (.checkov/accepted.toml) are validated.
# 2. Self-test: the deliberately insecure fixture must be reported exactly as
#    .checkov/fixtures/expected.txt says. A scanner that stopped catching things fails here.
# 3. Scan infra/; the policy gate decides what blocks. Checkov runs with --soft-fail so its own
#    exit code never decides: the gate does (same split as ZAP + dast_policy.py).
set -euo pipefail

OUT=${1:?usage: iac_scan.sh <output-dir>}
CHECKOV_IMAGE=${CHECKOV_IMAGE:-bridgecrew/checkov@sha256:8e63f217cb084f1c1a067326a9cf6e37d54bdc82e5822210d50ca4e2f647dd93} # 3.3.26
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SCAN_DIR=infra

mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
policy() { python3 "$ROOT/tools/security/iac_policy.py" "$@"; }
# As this user, repo read-only, output mounted separately. No API key: nothing leaves the runner
# (--skip-download: no policy metadata fetched from the vendor platform).
checkov() {
  local dir=$1 name=$2
  docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$ROOT:/src:ro" -w /src -v "$OUT:/out" \
    "$CHECKOV_IMAGE" -d "$dir" --framework terraform --external-checks-dir .checkov/checks \
    --download-external-modules false --skip-download --soft-fail --compact \
    -o json -o sarif --output-file-path "/out/$name" >/dev/null
}

echo "--- 1/3 accepted risks"
policy accepted "$ROOT/.checkov/accepted.toml"

echo "--- 2/3 self-test: the insecure fixture must be caught"
checkov .checkov/fixtures/insecure selftest
policy selftest "$OUT/selftest/results_json.json" "$ROOT/.checkov/fixtures/expected.txt"

echo "--- 3/3 scan $SCAN_DIR/"
if [ -z "$(find "$ROOT/$SCAN_DIR" -name '*.tf' -not -path '*/.terraform/*' 2>/dev/null | head -1)" ]; then
  echo "no Terraform under $SCAN_DIR/ yet: nothing to gate (the self-test above still proved the scanner)"
  exit 0
fi
checkov "$SCAN_DIR" infra
policy gate "$OUT/infra/results_json.json" --accepted "$ROOT/.checkov/accepted.toml" --dir "$ROOT/$SCAN_DIR"
