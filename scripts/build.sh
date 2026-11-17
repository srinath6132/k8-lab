#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?Usage: build.sh <service-name> <image:tag>}"
IMAGE_REF="${2:?Usage: build.sh <service-name> <image:tag>}"

case "$SERVICE" in
  user-service|auth-service|product-service|cart-service) ;;
  *) echo "Unsupported service: $SERVICE" >&2; exit 2 ;;
esac
if [[ ! "$IMAGE_REF" =~ ^[a-z0-9][a-z0-9._/-]*:[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]]; then
  echo "Invalid image reference: $IMAGE_REF" >&2
  exit 2
fi

docker build --pull --tag "$IMAGE_REF" --file "services/${SERVICE}/Dockerfile" .
trivy image --exit-code 1 --severity HIGH,CRITICAL "$IMAGE_REF"
