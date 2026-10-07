#!/usr/bin/env bash
# Image scan (G6): the backend and frontend container images and their Dockerfiles, with Trivy.
# Identical locally and in CI:  tools/security/image_scan.sh <output-dir>
# Policy: docs/security/image-scan.md
#
# 1. Accepted risks (.trivy/accepted.toml) are validated and rendered to Trivy's ignore file.
# 2. Dockerfile misconfigurations, from the repo root: Medium+ blocks. Dockerfiles only: Terraform
#    is G7's (Checkov, tools/security/iac_scan.sh), so one finding never shows up in two gates.
# 3. Each image is built and exported with `docker save`; Trivy reads the tar, so the scanner
#    never gets the Docker socket (which would be root on this machine).
# 4. One scan (all severities, all packages) feeds: coverage (it really saw the OS and the jar),
#    the gate (fixable Critical/High block), SARIF for code scanning and the image SBOM.
set -euo pipefail

OUT=${1:?usage: image_scan.sh <output-dir>}
TRIVY_IMAGE=${TRIVY_IMAGE:-aquasec/trivy@sha256:e2b22eac59c02003d8749f5b8d9bd073b62e30fefaef5b7c8371204e0a4b0c08} # 0.67.2
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CACHE=${TRIVY_CACHE:-$HOME/.cache/sevacenter-trivy}

mkdir -p "$OUT" "$CACHE"
OUT=$(cd "$OUT" && pwd)
policy() { python3 "$ROOT/tools/security/image_policy.py" "$@"; }
# As this user, repo read-only, cache and output mounted separately.
trivy() {
  docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$ROOT:/src:ro" -w /src \
    -v "$CACHE:/cache" -v "$OUT:/out" "$TRIVY_IMAGE" "$@" --cache-dir /cache --quiet
}

echo "--- 1/4 accepted risks"
policy ignores "$ROOT/.trivy/accepted.toml" --write "$OUT/trivyignore.yaml"

echo "--- 2/4 Dockerfile misconfigurations (Medium+ blocks)"
trivy config --misconfig-scanners dockerfile --ignorefile /out/trivyignore.yaml --skip-dirs frontend/node_modules --skip-dirs backend/target \
  --format json --output /out/trivy-config.json .
policy misconfig "$OUT/trivy-config.json" --fail-at MEDIUM

# Each image, with the package type its own code ships (coverage must find it).
scan_image() {
  local name=$1 expect=$2 image="sevacenter-$1:scan"
  echo "--- 3/4 build $image"
  docker build -q -t "$image" "$ROOT/$name" >/dev/null
  docker save "$image" -o "$OUT/$name.tar"
  echo "--- 4/4 scan $image"
  trivy image --input "/out/$name.tar" --scanners vuln --list-all-pkgs --timeout 15m \
    --ignorefile /out/trivyignore.yaml --format json --output "/out/trivy-$name.json"
  rm -f "$OUT/$name.tar"
  trivy convert --format sarif --output "/out/trivy-$name.sarif" "/out/trivy-$name.json"
  trivy convert --format cyclonedx --output "/out/$name.cdx.json" "/out/trivy-$name.json"
  policy coverage "$OUT/trivy-$name.json" --expect "$expect"
  policy gate "$OUT/trivy-$name.json"
}
scan_image backend jar
scan_image frontend node-pkg
