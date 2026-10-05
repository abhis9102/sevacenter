#!/usr/bin/env bash
# Image scan (G6): the backend container image and its Dockerfile, with Trivy.
# Identical locally and in CI:  tools/security/image_scan.sh <output-dir>
# Policy: docs/security/image-scan.md
#
# 1. Accepted risks (.trivy/accepted.toml) are validated and rendered to Trivy's ignore file.
# 2. Dockerfile / IaC misconfigurations, from the repo root: Medium+ blocks.
# 3. The image is built and exported with `docker save`; Trivy reads the tar, so the scanner
#    never gets the Docker socket (which would be root on this machine).
# 4. One scan (all severities, all packages) feeds: coverage (it really saw the OS and the jar),
#    the gate (fixable Critical/High block), SARIF for code scanning and the image SBOM.
set -euo pipefail

OUT=${1:?usage: image_scan.sh <output-dir>}
TRIVY_IMAGE=${TRIVY_IMAGE:-aquasec/trivy@sha256:e2b22eac59c02003d8749f5b8d9bd073b62e30fefaef5b7c8371204e0a4b0c08} # 0.67.2
IMAGE=${IMAGE:-sevacenter-backend:scan}
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

echo "--- 2/4 Dockerfile and IaC misconfigurations (Medium+ blocks)"
trivy config --ignorefile /out/trivyignore.yaml --skip-dirs frontend/node_modules --skip-dirs backend/target \
  --format json --output /out/trivy-config.json .
policy misconfig "$OUT/trivy-config.json" --fail-at MEDIUM

echo "--- 3/4 build $IMAGE"
docker build -q -t "$IMAGE" "$ROOT/backend" >/dev/null
docker save "$IMAGE" -o "$OUT/image.tar"

echo "--- 4/4 scan the image"
trivy image --input /out/image.tar --scanners vuln --list-all-pkgs --timeout 15m \
  --ignorefile /out/trivyignore.yaml --format json --output /out/trivy-image.json
rm -f "$OUT/image.tar"
trivy convert --format sarif --output /out/trivy-image.sarif /out/trivy-image.json
trivy convert --format cyclonedx --output /out/image.cdx.json /out/trivy-image.json
policy coverage "$OUT/trivy-image.json"
policy gate "$OUT/trivy-image.json"
