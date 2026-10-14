#!/usr/bin/env bash
# Terraform in CI (ADR 0033): plan or plan+apply one stack, in the digest-pinned Terraform image.
#   STACK=staging|bootstrap STATE_BUCKET=... tools/infra/terraform.sh plan|apply
# AWS credentials come from the environment (the OIDC step); TF_VAR_* pass straight through.
set -euo pipefail

MODE=${1:?usage: terraform.sh plan|apply}
: "${STACK:?}" "${STATE_BUCKET:?}" "${TF_IMAGE:?}"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
case "$STACK" in
  staging)   DIR=infra/envs/staging ;;
  bootstrap) DIR=infra/bootstrap; [ "$MODE" = plan ] || { echo "::error::bootstrap is applied by a human only"; exit 1; } ;;
  *) echo "::error::unknown stack $STACK"; exit 1 ;;
esac

tf() {
  docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e TF_IN_AUTOMATION=1 -e TF_INPUT=0 \
    -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN -e AWS_REGION \
    -e TF_VAR_alert_email -e TF_VAR_staff_base_domain -e TF_VAR_mandir_base_domain \
    -v "$ROOT:/src" -w "/src/$DIR" "$TF_IMAGE" "$@"
}

tf init -no-color -backend-config="bucket=$STATE_BUCKET" -backend-config="key=$STACK.tfstate" >/dev/null
tf plan -no-color -lock-timeout=120s -out=ci.tfplan | tee "$ROOT/tf-$STACK.txt"
if [ "$MODE" = apply ]; then
  tf apply -no-color -lock-timeout=120s ci.tfplan
fi
rm -f "$ROOT/$DIR/ci.tfplan"
